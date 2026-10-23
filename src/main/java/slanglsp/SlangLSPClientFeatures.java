package slanglsp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.client.features.EditorBehaviorFeature;
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.services.LanguageServer;
import org.jetbrains.annotations.NotNull;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.function.Consumer;

/**
 * {@link LSPClientFeatures} that makes the JSON launcher used to talk to {@code slangd} tolerate
 * two server quirks that would otherwise abort an LSP request (or the whole server):
 * ambiguous {@code Either} responses ({@link SlangEitherTypeAdapterFactory}) and empty JSON objects
 * sent for list-typed results ({@link SlangJsonRpcCompatibility}).
 * <p>
 * The method {@link #createLauncherBuilder()} is intentionally not annotated with
 * {@code @Override}: the LSP4IJ version this plugin is compiled against (0.13.0) predates that
 * hook. It has the exact same erased signature as the hook introduced by newer LSP4IJ versions,
 * so it is bound as an override whenever such a version is present at runtime (the version
 * shipped with current IDEs), and is simply never called on older ones.
 */
public class SlangLSPClientFeatures extends LSPClientFeatures {

    public SlangLSPClientFeatures() {
        // LSP4IJ's editor improvements are opt-in; enable the ones that make Slang feel like C#.
        setEditorBehaviorFeature(new EditorBehaviorFeature(this) {
            @Override
            public boolean isEnableStringLiteralImprovements(@NotNull PsiFile file) {
                return true;
            }

            @Override
            public boolean isEnableStatementTerminatorImprovements(@NotNull PsiFile file) {
                return true;
            }

            @Override
            public boolean isEnableEnterBetweenBracesFix(@NotNull PsiFile file) {
                return true;
            }
        });
    }

    private volatile Gson launcherGson;

    /**
     * Installs the empty-result compatibility layer on the launcher that is currently running.
     */
    void installResponseCompatibility() {
        Gson gson = launcherGson;
        if (gson != null) {
            SlangJsonRpcCompatibility.install(gson);
        }
    }

    public <S extends LanguageServer> Launcher.Builder<S> createLauncherBuilder() {
        Launcher.Builder<S> builder = createLSP4IJLauncherBuilder();
        if (builder == null) {
            builder = createDefaultLauncherBuilder();
        }
        if (builder == null) {
            throw new IllegalStateException("Unable to create an LSP launcher builder");
        }
        installEitherSupport(builder);
        return builder;
    }

    /**
     * Instantiates LSP4IJ's own launcher builder (the one that wires up its custom message
     * production/consumption) without a compile-time dependency on it, so this plugin keeps
     * compiling against older LSP4IJ releases.
     */
    @SuppressWarnings("unchecked")
    private <S extends LanguageServer> Launcher.Builder<S> createLSP4IJLauncherBuilder() {
        try {
            Class<?> builderClass = Class.forName("com.redhat.devtools.lsp4ij.server.DefaultLauncherBuilder");
            Constructor<?> constructor = builderClass.getConstructor(LSPClientFeatures.class);
            return (Launcher.Builder<S>) constructor.newInstance(this);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Last-resort fallback: invoke the (newer LSP4IJ) superclass implementation directly.
     */
    @SuppressWarnings("unchecked")
    private <S extends LanguageServer> Launcher.Builder<S> createDefaultLauncherBuilder() {
        try {
            MethodHandle handle = MethodHandles.lookup().findSpecial(
                    LSPClientFeatures.class,
                    "createLauncherBuilder",
                    MethodType.methodType(Launcher.Builder.class),
                    SlangLSPClientFeatures.class);
            return (Launcher.Builder<S>) handle.invoke(this);
        } catch (Throwable t) {
            return null;
        }
    }

    private void installEitherSupport(Launcher.Builder<?> builder) {
        try {
            Consumer<GsonBuilder> existing = readGsonConfigurer(builder);
            Consumer<GsonBuilder> combined = gsonBuilder -> {
                if (existing != null) {
                    existing.accept(gsonBuilder);
                }
                gsonBuilder.registerTypeAdapterFactory(new TypeAdapterFactory() {
                    @Override
                    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> typeToken) {
                        launcherGson = gson;
                        return null;
                    }
                });
                gsonBuilder.registerTypeAdapterFactory(new SlangEitherTypeAdapterFactory());
            };
            builder.configureGson(combined);
        } catch (Throwable ignored) {
            // Keep LSP4IJ's default JSON handling if the adapter cannot be installed.
        }
    }

    @SuppressWarnings("unchecked")
    private static Consumer<GsonBuilder> readGsonConfigurer(Launcher.Builder<?> builder) {
        try {
            Field field = Launcher.Builder.class.getDeclaredField("configureGson");
            field.setAccessible(true);
            return (Consumer<GsonBuilder>) field.get(builder);
        } catch (Throwable t) {
            return null;
        }
    }
}
