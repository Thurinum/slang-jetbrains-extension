package slanglsp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.InsertReplaceEdit;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.json.adapters.EitherTypeAdapter;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Type;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SlangEitherTypeAdapterFactoryTest {
    private static final Type TEXT_EDIT_EITHER = new TypeToken<Either<TextEdit, InsertReplaceEdit>>() {
    }.getType();
    private static final Type LOCATION_EITHER = new TypeToken<Either<List<Location>, List<LocationLink>>>() {
    }.getType();
    private static final Type DOCUMENT_SYMBOL_EITHER =
            new TypeToken<Either<List<DocumentSymbol>, List<SymbolInformation>>>() {
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

    /**
     * slangd answers {@code textDocument/documentSymbol} with {@code {}} while the document is not
     * open on the server yet. That used to abort the request (and the whole server) with
     * "Unexpected token BEGIN_OBJECT: expected BEGIN_ARRAY | BEGIN_ARRAY tokens".
     */
    @Test
    public void treatsEmptyObjectAsEmptyDocumentSymbolList() {
        Either<List<DocumentSymbol>, List<SymbolInformation>> either =
                gson().fromJson("{}", DOCUMENT_SYMBOL_EITHER);

        assertNotNull(either);
        assertTrue(either.isLeft(), "An empty object must become an empty document symbol list");
        assertTrue(either.getLeft().isEmpty());
    }

    @Test
    public void treatsEmptyObjectAsEmptyLocationList() {
        Either<List<Location>, List<LocationLink>> either = gson().fromJson("{}", LOCATION_EITHER);

        assertNotNull(either);
        assertTrue(either.isLeft(), "An empty object must become an empty location list");
        assertTrue(either.getLeft().isEmpty());
    }

    @Test
    public void stillRejectsObjectPayloadsThatAreNotTheEmptyObjectQuirk() {
        String json = "{\"range\":{\"start\":{\"line\":0,\"character\":0},\"end\":{\"line\":0,\"character\":1}}}";

        assertThrows(JsonParseException.class, () -> gson().fromJson(json, LOCATION_EITHER));
    }
}
