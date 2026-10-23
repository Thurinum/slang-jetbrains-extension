package slanglsp;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import slanglsp.highlighting.SlangSyntaxHighlighterColors;

public class SlangSyntaxHighlighter extends SyntaxHighlighterBase {
    private static final TextAttributesKey[] EMPTY = new TextAttributesKey[0];
    private static final TextAttributesKey[] KEYWORD_KEYS = {SlangSyntaxHighlighterColors.KEYWORD};
    private static final TextAttributesKey[] CONTROL_FLOW_KEYS = {SlangSyntaxHighlighterColors.CONTROL_FLOW};
    private static final TextAttributesKey[] CONSTANT_KEYS = {SlangSyntaxHighlighterColors.CONSTANT};
    private static final TextAttributesKey[] TYPE_KEYS = {SlangSyntaxHighlighterColors.TYPE_NAME};
    private static final TextAttributesKey[] FUNCTION_KEYS = {SlangSyntaxHighlighterColors.FUNCTION_CALL};
    private static final TextAttributesKey[] VARIABLE_KEYS = {SlangSyntaxHighlighterColors.VARIABLE};
    private static final TextAttributesKey[] MACRO_KEYS = {SlangSyntaxHighlighterColors.MACRO_KEYWORD};
    private static final TextAttributesKey[] PREPROCESSOR_KEYS = {SlangSyntaxHighlighterColors.PREPROCESSOR};
    private static final TextAttributesKey[] STRING_KEYS = {SlangSyntaxHighlighterColors.STRING};
    private static final TextAttributesKey[] NUMBER_KEYS = {SlangSyntaxHighlighterColors.NUMBER};
    private static final TextAttributesKey[] LINE_COMMENT_KEYS = {SlangSyntaxHighlighterColors.LINE_COMMENT};
    private static final TextAttributesKey[] BLOCK_COMMENT_KEYS = {SlangSyntaxHighlighterColors.BLOCK_COMMENT};
    private static final TextAttributesKey[] DOC_COMMENT_KEYS = {SlangSyntaxHighlighterColors.DOC_COMMENT};
    private static final TextAttributesKey[] OPERATOR_KEYS = {DefaultLanguageHighlighterColors.OPERATION_SIGN};
    private static final TextAttributesKey[] SEMICOLON_KEYS = {DefaultLanguageHighlighterColors.SEMICOLON};
    private static final TextAttributesKey[] COMMA_KEYS = {DefaultLanguageHighlighterColors.COMMA};
    private static final TextAttributesKey[] DOT_KEYS = {DefaultLanguageHighlighterColors.DOT};
    private static final TextAttributesKey[] BRACES_KEYS = {DefaultLanguageHighlighterColors.BRACES};
    private static final TextAttributesKey[] PARENTHESES_KEYS = {DefaultLanguageHighlighterColors.PARENTHESES};
    private static final TextAttributesKey[] BRACKETS_KEYS = {DefaultLanguageHighlighterColors.BRACKETS};

    @NotNull
    @Override
    public Lexer getHighlightingLexer() {
        return new SlangLexer();
    }

    @NotNull
    @Override
    public TextAttributesKey[] getTokenHighlights(IElementType tokenType) {
        if (tokenType.equals(SlangLexer.KEYWORD)) return KEYWORD_KEYS;
        if (tokenType.equals(SlangLexer.CONTROL_FLOW)) return CONTROL_FLOW_KEYS;
        if (tokenType.equals(SlangLexer.CONSTANT)) return CONSTANT_KEYS;
        if (tokenType.equals(SlangLexer.TYPE)) return TYPE_KEYS;
        if (tokenType.equals(SlangLexer.FUNCTION)) return FUNCTION_KEYS;
        if (tokenType.equals(SlangLexer.VARIABLE)) return VARIABLE_KEYS;
        if (tokenType.equals(SlangLexer.MACRO)) return MACRO_KEYS;
        if (tokenType.equals(SlangLexer.PREPROCESSOR)) return PREPROCESSOR_KEYS;
        if (tokenType.equals(SlangLexer.STRING)) return STRING_KEYS;
        if (tokenType.equals(SlangLexer.NUMBER)) return NUMBER_KEYS;
        if (tokenType.equals(SlangLexer.COMMENT)) return LINE_COMMENT_KEYS;
        if (tokenType.equals(SlangLexer.BLOCK_COMMENT)) return BLOCK_COMMENT_KEYS;
        if (tokenType.equals(SlangLexer.DOC_COMMENT)) return DOC_COMMENT_KEYS;
        if (tokenType.equals(SlangLexer.OPERATOR)) return OPERATOR_KEYS;
        if (tokenType.equals(SlangLexer.SEMICOLON)) return SEMICOLON_KEYS;
        if (tokenType.equals(SlangLexer.COMMA)) return COMMA_KEYS;
        if (tokenType.equals(SlangLexer.DOT)) return DOT_KEYS;
        if (tokenType.equals(SlangTokenTypes.L_CURLY) || tokenType.equals(SlangTokenTypes.R_CURLY)) return BRACES_KEYS;
        if (tokenType.equals(SlangTokenTypes.L_PARENTHESIS) || tokenType.equals(SlangTokenTypes.R_PARENTHESIS)) return PARENTHESES_KEYS;
        if (tokenType.equals(SlangTokenTypes.L_BRACKET) || tokenType.equals(SlangTokenTypes.R_BRACKET)) return BRACKETS_KEYS;
        return EMPTY;
    }
}
