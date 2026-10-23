package slanglsp;

import com.google.gson.JsonParseException;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.Message;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage;
import org.eclipse.lsp4j.jsonrpc.services.ServiceEndpoints;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * slangd answers list-typed requests with {@code {}} when there is no result (for example
 * {@code textDocument/definition} on a symbol without a definition, or
 * {@code textDocument/documentSymbol} before the file was opened on the server). LSP4J cannot
 * decode that and the exception kills the message loop, which surfaces as "Cannot start server".
 */
public class SlangJsonRpcCompatibilityTest {
    private static final String RESPONSE = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":%s}";
    private static final String EMPTY_RESULT = String.format(RESPONSE, "{}");

    private static MessageJsonHandler handler(String method) {
        MessageJsonHandler handler = new MessageJsonHandler(
                ServiceEndpoints.getSupportedMethods(TextDocumentService.class));
        handler.setMethodProvider(id -> method);
        return handler;
    }

    @Test
    public void emptyObjectCannotBeParsedByLsp4jAlone() {
        assertThrows(JsonParseException.class,
                () -> handler("textDocument/definition").parseMessage(EMPTY_RESULT));
    }

    @Test
    public void emptyObjectBecomesAnEmptyLocationList() throws Exception {
        MessageJsonHandler handler = handler("textDocument/definition");
        SlangJsonRpcCompatibility.install(handler);

        Message message = handler.parseMessage(EMPTY_RESULT);

        Either<?, ?> result = (Either<?, ?>) ((ResponseMessage) message).getResult();
        assertNotNull(result);
        assertTrue(result.isLeft());
        assertTrue(((List<?>) result.getLeft()).isEmpty());
    }

    @Test
    public void emptyObjectBecomesAnEmptyDocumentSymbolList() throws Exception {
        MessageJsonHandler handler = handler("textDocument/documentSymbol");
        SlangJsonRpcCompatibility.install(handler);

        Message message = handler.parseMessage(EMPTY_RESULT);

        Object result = ((ResponseMessage) message).getResult();
        assertTrue(result instanceof List, "Expected a plain list for documentSymbol");
        assertTrue(((List<?>) result).isEmpty());
    }

    @Test
    public void realDefinitionResponseIsUnchanged() throws Exception {
        MessageJsonHandler handler = handler("textDocument/definition");
        SlangJsonRpcCompatibility.install(handler);

        Message message = handler.parseMessage(String.format(RESPONSE,
                "[{\"uri\":\"file:///a.slang\",\"range\":{\"start\":{\"line\":0,\"character\":0},"
                        + "\"end\":{\"line\":0,\"character\":1}}}]"));

        Either<?, ?> result = (Either<?, ?>) ((ResponseMessage) message).getResult();
        assertNotNull(result);
        assertTrue(result.isLeft());
        Object first = ((List<?>) result.getLeft()).get(0);
        assertTrue(first instanceof Location);
        assertEquals("file:///a.slang", ((Location) first).getUri());
    }
}
