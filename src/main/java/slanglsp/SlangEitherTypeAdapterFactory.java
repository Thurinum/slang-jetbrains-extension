package slanglsp;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import org.eclipse.lsp4j.jsonrpc.json.adapters.EitherTypeAdapter;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Gson {@link TypeAdapterFactory} that removes "Ambiguous Either type" parse failures.
 * <p>
 * LSP4J models a number of protocol fields as {@code Either<A, B>} where both {@code A} and
 * {@code B} are JSON objects (for example completion text edits and various
 * "options vs. registration options" pairs). When a language server sends an object for such a
 * field, the stock {@code EitherTypeAdapter} cannot tell which alternative is meant and throws:
 * <pre>
 * com.google.gson.JsonParseException: Ambiguous Either type: token BEGIN_OBJECT matches both alternatives.
 * </pre>
 * That exception aborts whatever request/response is being processed - including the
 * {@code initialize} handshake, which surfaces to the user as a "cannot start language server" error.
 * <p>
 * This factory installs a checker for exactly those object/object {@code Either} types: it scores
 * both alternatives by counting how many of their declared fields are present on the received JSON
 * object and picks the closer match (ties prefer the left alternative). All other {@code Either}
 * shapes are left to LSP4J.
 * <p>
 * It also copes with {@code slangd} answering an empty JSON object ({@code {}}) for requests whose
 * protocol result is a list. For example {@code textDocument/documentSymbol} returns {@code {}} when
 * the document has not been opened on the server yet, while LSP4J expects
 * {@code Either<List<DocumentSymbol>, List<SymbolInformation>>} and therefore throws:
 * <pre>
 * com.google.gson.JsonParseException: Unexpected token BEGIN_OBJECT: expected BEGIN_ARRAY | BEGIN_ARRAY tokens.
 * </pre>
 * Such a payload is mapped to an empty list instead, which is what the server meant by it.
 */
public class SlangEitherTypeAdapterFactory implements TypeAdapterFactory {

    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> typeToken) {
        Type type = typeToken.getType();
        if (!(type instanceof ParameterizedType)) {
            return null;
        }
        ParameterizedType parameterizedType = (ParameterizedType) type;
        if (parameterizedType.getRawType() != Either.class) {
            return null;
        }

        Type[] typeArguments = parameterizedType.getActualTypeArguments();
        if (typeArguments.length != 2) {
            return null;
        }

        Class<?> left = rawType(typeArguments[0]);
        Class<?> right = rawType(typeArguments[1]);
        if (left == null || right == null) {
            return null;
        }
        // Leave Object-based Eithers (e.g. generic response payloads) and primitive/collection
        // alternatives to the stock adapter - they are never ambiguous.
        if (left == Object.class || right == Object.class) {
            return null;
        }

        if (expectsArray(left) && expectsArray(right)) {
            // Both alternatives are lists, so an empty JSON object cannot be a valid result.
            // It is slangd's way of saying "nothing here"; map it to an empty list.
            @SuppressWarnings({"unchecked", "rawtypes"})
            TypeAdapter<T> adapter = (TypeAdapter<T>) new EmptyObjectAsEmptyListAdapter(
                    gson.getDelegateAdapter(this, typeToken));
            return adapter;
        }

        if (!isObjectLike(left) || !isObjectLike(right)) {
            return null;
        }

        Predicate<JsonElement> preferLeft = element -> preferLeft(element, left, right);
        Predicate<JsonElement> preferRight = element -> !preferLeft(element, left, right);

        @SuppressWarnings({"unchecked", "rawtypes"})
        TypeAdapter<T> adapter = (TypeAdapter<T>) new EitherTypeAdapter(gson, (TypeToken) typeToken, preferLeft, preferRight);
        return adapter;
    }

    /**
     * Adapter for {@code Either<List<A>, List<B>>} results that treats an empty JSON object as an
     * empty list. Everything else is delegated to the adapter LSP4J would have used otherwise.
     */
    private static class EmptyObjectAsEmptyListAdapter<L, R> extends TypeAdapter<Either<L, R>> {
        private final TypeAdapter<Either<L, R>> delegate;

        EmptyObjectAsEmptyListAdapter(TypeAdapter<Either<L, R>> delegate) {
            this.delegate = delegate;
        }

        @Override
        public void write(JsonWriter out, Either<L, R> value) throws IOException {
            delegate.write(out, value);
        }

        @Override
        public Either<L, R> read(JsonReader in) throws IOException {
            if (in.peek() != JsonToken.BEGIN_OBJECT) {
                return delegate.read(in);
            }

            JsonElement element = JsonParser.parseReader(in);
            if (element.isJsonObject() && element.getAsJsonObject().isEmpty()) {
                @SuppressWarnings("unchecked")
                L emptyList = (L) Collections.emptyList();
                return Either.forLeft(emptyList);
            }

            // Not the slangd "no result" payload: let LSP4J parse it and report its usual error.
            return delegate.read(new JsonReader(new StringReader(element.toString())));
        }
    }

    private static boolean preferLeft(JsonElement element, Class<?> left, Class<?> right) {
        if (!element.isJsonObject()) {
            return true;
        }
        JsonObject object = element.getAsJsonObject();
        return fieldMatchScore(object, left) >= fieldMatchScore(object, right);
    }

    private static int fieldMatchScore(JsonObject object, Class<?> type) {
        int score = 0;
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers) || field.isSynthetic()) {
                    continue;
                }
                SerializedName serializedName = field.getAnnotation(SerializedName.class);
                String name = serializedName != null ? serializedName.value() : field.getName();
                if (object.has(name)) {
                    score++;
                }
            }
        }
        return score;
    }

    private static boolean isObjectLike(Class<?> type) {
        return !(type.isArray()
                || Collection.class.isAssignableFrom(type)
                || Map.class.isAssignableFrom(type)
                || Boolean.class.isAssignableFrom(type)
                || Number.class.isAssignableFrom(type)
                || Enum.class.isAssignableFrom(type)
                || Character.class.isAssignableFrom(type)
                || CharSequence.class.isAssignableFrom(type));
    }

    private static boolean expectsArray(Class<?> type) {
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
}
