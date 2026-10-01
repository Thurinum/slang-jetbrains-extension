package slanglsp;

import com.intellij.psi.tree.IElementType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SlangLexerTest {
    @Test
    public void testLexerHeuristics() {
        SlangLexer lexer = new SlangLexer();
        String code = "struct Tensor { int a; void main() { } }";
        lexer.start(code);
        assertNotNull(lexer.getTokenType(), "Lexer should start with a valid token");
    }

    @Test
    public void testBraceTokenTypes() {
        SlangLexer lexer = new SlangLexer();
        lexer.start("{}()[]");

        List<IElementType> types = new ArrayList<>();
        while (lexer.getTokenType() != null) {
            types.add(lexer.getTokenType());
            lexer.advance();
        }

        assertEquals(
                List.of(
                        SlangTokenTypes.L_CURLY,
                        SlangTokenTypes.R_CURLY,
                        SlangTokenTypes.L_PARENTHESIS,
                        SlangTokenTypes.R_PARENTHESIS,
                        SlangTokenTypes.L_BRACKET,
                        SlangTokenTypes.R_BRACKET),
                types);
    }

    @Test
    public void testUnterminatedStringStopsAtNewline() {
        SlangLexer lexer = new SlangLexer();
        String code = "var s = \"unterminated\nvar t = 1;";
        lexer.start(code);

        int stringTokens = 0;
        for (; lexer.getTokenType() != null; lexer.advance()) {
            if (lexer.getTokenType() == SlangLexer.STRING) {
                stringTokens++;
                assertTrue(lexer.getTokenEnd() <= code.indexOf('\n') + 1,
                        "An unterminated string must not swallow the rest of the file");
            }
        }
        assertEquals(1, stringTokens, "The stray quote should produce exactly one string token");
    }
}

