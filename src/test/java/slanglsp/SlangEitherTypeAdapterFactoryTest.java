package slanglsp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.eclipse.lsp4j.InsertReplaceEdit;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.json.adapters.EitherTypeAdapter;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SlangEitherTypeAdapterFactoryTest {
    private static final Type TEXT_EDIT_EITHER = new TypeToken<Either<TextEdit, InsertReplaceEdit>>() {
    }.getType();

    private static Gson gson() {
        return new GsonBuilder()
                .registerTypeAdapterFactory(new EitherTypeAdapter.Factory())
                .registerTypeAdapterFactory(new SlangEitherTypeAdapterFactory())
                .create();
    }

    @Test
    public void resolvesWholeRangeTextEditAlternative() {
        String json = "{\"range\":{\"start\":{\"line\":0,\"character\":0},\"end\":{\"line\":0,\"character\":1}},\"newText\":\"x\"}";

        Either<TextEdit, InsertReplaceEdit> either = gson().fromJson(json, TEXT_EDIT_EITHER);

        assertTrue(either.isLeft(), "A TextEdit payload must resolve to the left alternative");
        assertEquals("x", either.getLeft().getNewText());
    }

    @Test
    public void resolvesInsertReplaceAlternative() {
        String json = "{\"insert\":{\"start\":{\"line\":0,\"character\":0},\"end\":{\"line\":0,\"character\":1}},"
                + "\"replace\":{\"start\":{\"line\":0,\"character\":0},\"end\":{\"line\":0,\"character\":5}}}";

        Either<TextEdit, InsertReplaceEdit> either = gson().fromJson(json, TEXT_EDIT_EITHER);

        assertTrue(either.isRight(), "An InsertReplaceEdit payload must resolve to the right alternative");
    }
}
