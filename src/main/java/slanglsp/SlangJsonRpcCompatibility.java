package slanglsp;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import org.eclipse.lsp4j.jsonrpc.json.JsonRpcMethod;
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler;
import org.eclipse.lsp4j.jsonrpc.json.adapters.MessageTypeAdapter;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.Message;

import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Makes LSP4J tolerant of the way {@code slangd} reports "no result".
 * <p>
 * For requests whose protocol result is a list, {@code slangd} answers with an empty JSON object
 * ({@code {}}) instead of an empty array ({@code []}). For example a {@code textDocument/definition}
 * on a symbol that has no definition returns {@code {}}. LSP4J models such results as
 * {@code Either<List<Location>, List<LocationLink>>} and rejects the payload with:
 * <pre>
 * com.google.gson.JsonParseException: Unexpected token BEGIN_OBJECT: expected BEGIN_ARRAY | BEGIN_ARRAY tokens.
 * </pre>
 * That exception is thrown while the response is decoded, so it kills the message reader future of
 * LSP4IJ. On the next server start LSP4IJ notices the failed future and reports
 * "Cannot start server", then stops the server - which is why all IntelliSense stops working.
 * <p>
 * These responses are decoded by the {@code TypeAdapterFactory} referenced by LSP4J's
 * {@link JsonRpcMethod} (for {@code textDocument/definition} that is LSP4J's
 * {@code LocationLinkListAdapter}), which bypasses the {@link Gson} factories installed through
 * {@code Launcher.Builder.configureGson()}. This class therefore rewrites those factories on the
 * live {@link MessageJsonHandler}, and wraps them so that an empty JSON object becomes an empty
 * list while everything else is parsed exactly as before.
 */
final class SlangJsonRpcCompatibility {

    private static final Logger LOG = Logger.getLogger(SlangJsonRpcCompatibility.class.getName());

    private SlangJsonRpcCompatibility() {
    }

    /**
     * Installs the compatibility layer on the message handler that belongs to the given
     * {@link Gson}. Safe to call more than once.
     */
    static void install(Gson gson) {
        if (gson == null) {
            return;
        }
        try {
            Object messageAdapter = gson.getAdapter(Message.class);
            if (!(messageAdapter instanceof MessageTypeAdapter)) {
                return;
            }
            Field handlerField = findField(messageAdapter.getClass(), "handler");
            handlerField.setAccessible(true);
            Object handler = handlerField.get(messageAdapter);
            if (handler instanceof MessageJsonHandler) {
                install((MessageJsonHandler) handler);
            }
        } catch (Throwable t) {
            LOG.log(Level.WARNING, "Unable to install the Slang empty-result compatibility layer", t);
        }
    }

    @SuppressWarnings("unchecked")
    static void install(MessageJsonHandler handler) throws ReflectiveOperationException {
        Field methodsField = findField(MessageJsonHandler.class, "supportedMethods");
        methodsField.setAccessible(true);
        Map<String, JsonRpcMethod> methods = (Map<String, JsonRpcMethod>) methodsField.get(handler);

        for (Map.Entry<String, JsonRpcMethod> entry : new ArrayList<>(methods.entrySet())) {
            JsonRpcMethod method = entry.getValue();
            if (method.isNotification() || !isListShapedResult(method.getReturnType())) {
                continue;
            }
            TypeAdapterFactory factory = method.getReturnTypeAdapterFactory();
            if (factory == null || factory instanceof EmptyObjectTolerantFactory) {
                // A null factory means the result is decoded by the Gson factories, where
                // SlangEitherTypeAdapterFactory already handles the empty object.
                continue;
            }
            methods.put(entry.getKey(), JsonRpcMethod.request(
                    method.getMethodName(),
                    method.getReturnType(),
                    new EmptyObjectTolerantFactory(factory, isEither(method.getReturnType())),
                    method.getParameterTypes()));
        }
    }

    /**
     * @return {@code true} for results that are a JSON array, either directly
     * ({@code List<...>}) or as a list alternative ({@code Either<List<A>, List<B>>}).
     */
    static boolean isListShapedResult(Type type) {
        Class<?> raw = rawType(type);
        if (raw == null) {
            return false;
        }
        if (raw.isArray() || Collection.class.isAssignableFrom(raw)) {
            return true;
        }
        if (raw != Either.class || !(type instanceof ParameterizedType)) {
            return false;
        }
        Type[] arguments = ((ParameterizedType) type).getActualTypeArguments();
        return arguments.length == 2
                && expectsArray(rawType(arguments[0]))
                && expectsArray(rawType(arguments[1]));
    }

    private static boolean isEither(Type type) {
        return rawType(type) == Either.class;
    }

    private static boolean expectsArray(Class<?> type) {
        if (type == null) {
            return false;
        }
        return type.isArray() || Collection.class.isAssignableFrom(type);
    }

    private static Class<?> rawType(Type type) {
        if (type instanceof Class) {
            return (Class<?>) type;
        }
        if (type instanceof ParameterizedType) {
            Type raw = ((ParameterizedType) type).getRawType();
            return raw instanceof Class ? (Class<?>) raw : null;
        }
        return null;
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // Keep walking up the hierarchy.
            }
        }
        throw new NoSuchFieldException(name);
    }

    /**
     * Wraps the response adapter LSP4J would normally use, turning an empty JSON object into an
     * empty list (the meaning of {@code {}} in {@code slangd} responses) and delegating all other
     * payloads unchanged.
     */
    private static final class EmptyObjectTolerantFactory implements TypeAdapterFactory {
        private final TypeAdapterFactory delegate;
        private final boolean eitherResult;

        EmptyObjectTolerantFactory(TypeAdapterFactory delegate, boolean eitherResult) {
            this.delegate = delegate;
            this.eitherResult = eitherResult;
        }

        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> typeToken) {
            TypeAdapter<T> adapter = delegate.create(gson, typeToken);
            if (adapter == null) {
                return null;
            }
            return new TypeAdapter<T>() {
                @Override
                public void write(JsonWriter out, T value) throws IOException {
                    adapter.write(out, value);
                }

                @Override
                public T read(JsonReader in) throws IOException {
                    if (in.peek() != JsonToken.BEGIN_OBJECT) {
                        return adapter.read(in);
                    }

                    JsonElement element = JsonParser.parseReader(in);
                    if (element.isJsonObject() && element.getAsJsonObject().isEmpty()) {
                        @SuppressWarnings("unchecked")
                        T emptyResult = (T) (eitherResult
                                ? Either.forLeft(Collections.emptyList())
                                : Collections.emptyList());
                        return emptyResult;
                    }

                    // Not the slangd "no result" payload: let LSP4J parse and report as usual.
                    return adapter.read(new JsonReader(new StringReader(element.toString())));
                }
            };
        }
    }
}
