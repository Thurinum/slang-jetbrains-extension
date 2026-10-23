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

    @Test
    public void testBlockAndDocComments() {
        SlangLexer lexer = new SlangLexer();
        lexer.start("/* block\ncomment */ /** doc */ /// line doc\nint x;");

        List<IElementType> types = new ArrayList<>();
        for (; lexer.getTokenType() != null; lexer.advance()) {
            types.add(lexer.getTokenType());
        }

        assertTrue(types.contains(SlangLexer.BLOCK_COMMENT), "Block comments must be recognized");
        assertEquals(2, types.stream().filter(type -> type == SlangLexer.DOC_COMMENT).count(),
                "Both /** */ and /// comments must be doc comments");
    }

    @Test
    public void testPreprocessorAndMacros() {
        SlangLexer lexer = new SlangLexer();
        lexer.start("#define MAX_VALUE 10\n#include \"common.h.slang\"");

        List<IElementType> types = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        collect(lexer, types, texts);

        assertEquals(SlangLexer.PREPROCESSOR, types.get(0));
        assertEquals("#define", texts.get(0));
        assertTrue(types.contains(SlangLexer.MACRO), "MAX_VALUE should be a macro");
        assertTrue(types.contains(SlangLexer.STRING), "The include path should be a string");
    }

    @Test
    public void testBuiltinTypesAreTypes() {
        SlangLexer lexer = new SlangLexer();
        lexer.start("float4 a; uint32_t b; float4x4 m; vector<float, 4> v;");

        List<IElementType> types = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        collect(lexer, types, texts);

        assertEquals(SlangLexer.TYPE, typeOf(types, texts, "float4"));
        assertEquals(SlangLexer.TYPE, typeOf(types, texts, "uint32_t"));
        assertEquals(SlangLexer.TYPE, typeOf(types, texts, "float4x4"));
        assertEquals(SlangLexer.TYPE, typeOf(types, texts, "vector"));
    }

    @Test
    public void testNumberFormats() {
        SlangLexer lexer = new SlangLexer();
        lexer.start("0x1Fu 0b1010 1.0e-3f .5 42ull 1_000");

        for (; lexer.getTokenType() != null; lexer.advance()) {
            if (lexer.getTokenType() != SlangLexer.TEXT) {
                assertEquals(SlangLexer.NUMBER, lexer.getTokenType(),
                        "Numeric literal should be a single token: "
                                + lexer.getBufferSequence().subSequence(lexer.getTokenStart(), lexer.getTokenEnd()));
            }
        }
    }

    @Test
    public void testControlFlowAndConstants() {
        SlangLexer lexer = new SlangLexer();
        lexer.start("if (true) return false;");

        List<IElementType> types = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        collect(lexer, types, texts);

        assertEquals(SlangLexer.CONTROL_FLOW, typeOf(types, texts, "if"));
        assertEquals(SlangLexer.CONSTANT, typeOf(types, texts, "true"));
        assertEquals(SlangLexer.CONTROL_FLOW, typeOf(types, texts, "return"));
        assertEquals(SlangLexer.CONSTANT, typeOf(types, texts, "false"));
    }

    @Test
    public void testOperatorsAndPunctuation() {
        SlangLexer lexer = new SlangLexer();
        lexer.start("a += b << 2;");

        List<IElementType> types = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        collect(lexer, types, texts);

        assertEquals(SlangLexer.OPERATOR, typeOf(types, texts, "+="));
        assertEquals(SlangLexer.OPERATOR, typeOf(types, texts, "<<"));
        assertEquals(SlangLexer.SEMICOLON, types.get(types.size() - 1));
    }

    private static void collect(SlangLexer lexer, List<IElementType> types, List<String> texts) {
        for (; lexer.getTokenType() != null; lexer.advance()) {
            types.add(lexer.getTokenType());
            texts.add(lexer.getBufferSequence().subSequence(lexer.getTokenStart(), lexer.getTokenEnd()).toString());
        }
    }

    private static IElementType typeOf(List<IElementType> types, List<String> texts, String text) {
        for (int i = 0; i < texts.size(); i++) {
            if (texts.get(i).equals(text)) {
                return types.get(i);
            }
        }
        return null;
    }
}

