package slanglsp;

import com.intellij.lexer.LexerBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.regex.Pattern;

public class SlangLexer extends LexerBase {
    private CharSequence buffer;
    private int startOffset;
    private int endOffset;
    private int position;
    private int tokenStart;
    private int tokenEnd;
    private IElementType tokenType;

    public static final IElementType KEYWORD = new IElementType("KEYWORD", SlangLanguage.INSTANCE);
    public static final IElementType CONTROL_FLOW = new IElementType("CONTROL_FLOW", SlangLanguage.INSTANCE);
    public static final IElementType CONSTANT = new IElementType("CONSTANT", SlangLanguage.INSTANCE);
    public static final IElementType TYPE = new IElementType("TYPE", SlangLanguage.INSTANCE);
    public static final IElementType FUNCTION = new IElementType("FUNCTION", SlangLanguage.INSTANCE);
    public static final IElementType VARIABLE = new IElementType("VARIABLE", SlangLanguage.INSTANCE);
    public static final IElementType MACRO = new IElementType("MACRO", SlangLanguage.INSTANCE);
    public static final IElementType PREPROCESSOR = new IElementType("PREPROCESSOR", SlangLanguage.INSTANCE);
    public static final IElementType STRING = new IElementType("STRING", SlangLanguage.INSTANCE);
    public static final IElementType NUMBER = new IElementType("NUMBER", SlangLanguage.INSTANCE);
    public static final IElementType COMMENT = new IElementType("COMMENT", SlangLanguage.INSTANCE);
    public static final IElementType BLOCK_COMMENT = new IElementType("BLOCK_COMMENT", SlangLanguage.INSTANCE);
    public static final IElementType DOC_COMMENT = new IElementType("DOC_COMMENT", SlangLanguage.INSTANCE);
    public static final IElementType OPERATOR = new IElementType("OPERATOR", SlangLanguage.INSTANCE);
    public static final IElementType SEMICOLON = new IElementType("SEMICOLON", SlangLanguage.INSTANCE);
    public static final IElementType COMMA = new IElementType("COMMA", SlangLanguage.INSTANCE);
    public static final IElementType DOT = new IElementType("DOT", SlangLanguage.INSTANCE);
    public static final IElementType TEXT = new IElementType("TEXT", SlangLanguage.INSTANCE);

    private static final Set<String> KEYWORDS = Set.of(
            "struct", "interface", "enum", "extension", "typealias", "associatedtype", "let", "var", "void", "return",
            "if", "else", "for", "while", "do", "switch", "case", "default", "break", "continue", "static", "const",
            "true", "false", "in", "out", "inout", "__init", "operator", "public", "private", "protected", "internal",
            "import", "module", "implementing", "property", "get", "set", "yield", "defer", "as", "is", "reinterpret",
            "sizeof", "alignof", "typeof", "groupshared", "uniform", "inline", "extern", "export", "class",
            "namespace", "using", "typedef", "this", "discard", "null", "nullptr", "mutating", "nonmutating",
            "throw", "try", "catch"
    );

    private static final Set<String> CONTROL_FLOW_WORDS = Set.of(
            "if", "else", "for", "while", "do", "switch", "case", "default", "break", "continue", "return",
            "discard", "throw", "try", "catch", "defer", "yield"
    );

    private static final Set<String> CONSTANT_WORDS = Set.of("true", "false", "null", "nullptr");

    /**
     * Builtin scalar/vector/matrix types. Keyword/control-flow words are checked first, so
     * {@code void} stays a keyword.
     */
    private static final Pattern BUILTIN_TYPE = Pattern.compile(
            "(?:bool|half|float|double|u?int)(?:8|16|32|64)?(?:_t)?(?:[1-4](?:x[1-4])?)?"
                    + "|(?:vector|matrix)"
    );

    /**
     * Sorted longest-first so {@code <<=} is matched before {@code <<} and {@code <}.
     */
    private static final String[] OPERATORS = {
            "<<=", ">>=",
            "++", "--", "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=",
            "==", "!=", "<=", ">=", "&&", "||", "<<", ">>", "->", "::", "=>",
            "+", "-", "*", "/", "%", "=", "<", ">", "!", "&", "|", "^", "~", "?", ":"
    };

    public static boolean isKeyword(@NotNull String word) {
        return KEYWORDS.contains(word);
    }

    public static @NotNull Set<String> keywords() {
        return KEYWORDS;
    }

    @Override
    public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
        this.buffer = buffer;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.position = startOffset;
        advance();
    }

    @Override
    public int getState() {
        return 0;
    }

    @Nullable
    @Override
    public IElementType getTokenType() {
        return tokenType;
    }

    @Override
    public int getTokenStart() {
        return tokenStart;
    }

    @Override
    public int getTokenEnd() {
        return tokenEnd;
    }

    @Override
    public void advance() {
        if (position >= endOffset) {
            tokenType = null;
            return;
        }

        tokenStart = position;
        char c = buffer.charAt(position);

        if (Character.isWhitespace(c)) {
            while (position < endOffset && Character.isWhitespace(buffer.charAt(position))) {
                position++;
            }
            tokenType = TEXT;
        } else if (c == '/' && position + 1 < endOffset && buffer.charAt(position + 1) == '/') {
            boolean doc = position + 2 < endOffset && buffer.charAt(position + 2) == '/';
            while (position < endOffset && buffer.charAt(position) != '\n' && buffer.charAt(position) != '\r') {
                position++;
            }
            tokenType = doc ? DOC_COMMENT : COMMENT;
        } else if (c == '/' && position + 1 < endOffset && buffer.charAt(position + 1) == '*') {
            boolean doc = position + 2 < endOffset && buffer.charAt(position + 2) == '*';
            scanBlockComment();
            tokenType = doc ? DOC_COMMENT : BLOCK_COMMENT;
        } else if (c == '"' || c == '\'') {
            scanString(c);
            tokenType = STRING;
        } else if (Character.isDigit(c) || (c == '.' && position + 1 < endOffset && Character.isDigit(buffer.charAt(position + 1)))) {
            scanNumber();
            tokenType = NUMBER;
        } else if (isIdentifierStart(c)) {
            scanIdentifier();
        } else if (c == '#') {
            scanPreprocessor();
        } else if (c == '{') {
            position++;
            tokenType = SlangTokenTypes.L_CURLY;
        } else if (c == '}') {
            position++;
            tokenType = SlangTokenTypes.R_CURLY;
        } else if (c == '(') {
            position++;
            tokenType = SlangTokenTypes.L_PARENTHESIS;
        } else if (c == ')') {
            position++;
            tokenType = SlangTokenTypes.R_PARENTHESIS;
        } else if (c == '[') {
            position++;
            tokenType = SlangTokenTypes.L_BRACKET;
        } else if (c == ']') {
            position++;
            tokenType = SlangTokenTypes.R_BRACKET;
        } else if (c == ';') {
            position++;
            tokenType = SEMICOLON;
        } else if (c == ',') {
            position++;
            tokenType = COMMA;
        } else if (c == '.') {
            position++;
            tokenType = DOT;
        } else if (scanOperator()) {
            tokenType = OPERATOR;
        } else {
            position++;
            tokenType = TEXT;
        }
        tokenEnd = position;
    }

    private void scanBlockComment() {
        position += 2;
        while (position + 1 < endOffset) {
            if (buffer.charAt(position) == '*' && buffer.charAt(position + 1) == '/') {
                position += 2;
                return;
            }
            position++;
        }
        position = endOffset;
    }

    private void scanString(char quote) {
        if (quote == '"' && position + 2 < endOffset
                && buffer.charAt(position + 1) == '"' && buffer.charAt(position + 2) == '"') {
            // Triple-quoted string literal; may span lines.
            position += 3;
            while (position + 2 < endOffset) {
                if (buffer.charAt(position) == '"' && buffer.charAt(position + 1) == '"'
                        && buffer.charAt(position + 2) == '"') {
                    position += 3;
                    return;
                }
                position++;
            }
            position = endOffset;
            return;
        }

        position++;
        while (position < endOffset) {
            char ch = buffer.charAt(position);
            if (ch == '\\' && position + 1 < endOffset) {
                position += 2;
                continue;
            }
            if (ch == quote || ch == '\n' || ch == '\r') {
                break;
            }
            position++;
        }
        if (position < endOffset && buffer.charAt(position) == quote) {
            position++;
        }
    }

    private void scanNumber() {
        if (buffer.charAt(position) == '.') {
            position++;
            while (position < endOffset && isDigitOrUnderscore(buffer.charAt(position))) {
                position++;
            }
        } else if (position + 1 < endOffset && buffer.charAt(position) == '0'
                && (buffer.charAt(position + 1) == 'x' || buffer.charAt(position + 1) == 'X')) {
            position += 2;
            while (position < endOffset && (Character.digit(buffer.charAt(position), 16) >= 0
                    || buffer.charAt(position) == '_')) {
                position++;
            }
        } else if (position + 1 < endOffset && buffer.charAt(position) == '0'
                && (buffer.charAt(position + 1) == 'b' || buffer.charAt(position + 1) == 'B')) {
            position += 2;
            while (position < endOffset && (buffer.charAt(position) == '0' || buffer.charAt(position) == '1'
                    || buffer.charAt(position) == '_')) {
                position++;
            }
        } else {
            while (position < endOffset && isDigitOrUnderscore(buffer.charAt(position))) {
                position++;
            }
            if (position < endOffset && buffer.charAt(position) == '.'
                    && position + 1 < endOffset && Character.isDigit(buffer.charAt(position + 1))) {
                position++;
                while (position < endOffset && isDigitOrUnderscore(buffer.charAt(position))) {
                    position++;
                }
            }
            if (position < endOffset && (buffer.charAt(position) == 'e' || buffer.charAt(position) == 'E')) {
                int exponentStart = position;
                position++;
                if (position < endOffset && (buffer.charAt(position) == '+' || buffer.charAt(position) == '-')) {
                    position++;
                }
                if (position < endOffset && Character.isDigit(buffer.charAt(position))) {
                    while (position < endOffset && isDigitOrUnderscore(buffer.charAt(position))) {
                        position++;
                    }
                } else {
                    position = exponentStart;
                }
            }
        }

        // Numeric suffixes: 1.0f, 2h, 3u, 4ull, ...
        while (position < endOffset && "fFhHlLuU".indexOf(buffer.charAt(position)) >= 0) {
            position++;
        }
    }

    private static boolean isDigitOrUnderscore(char c) {
        return Character.isDigit(c) || c == '_';
    }

    private void scanIdentifier() {
        int wordStart = position;
        while (position < endOffset && isIdentifierPart(buffer.charAt(position))) {
            position++;
        }
        String word = buffer.subSequence(wordStart, position).toString();

        if (CONSTANT_WORDS.contains(word)) {
            tokenType = CONSTANT;
        } else if (KEYWORDS.contains(word)) {
            tokenType = CONTROL_FLOW_WORDS.contains(word) ? CONTROL_FLOW : KEYWORD;
        } else if (BUILTIN_TYPE.matcher(word).matches()) {
            tokenType = TYPE;
        } else if (isMacroName(word)) {
            tokenType = MACRO;
        } else {
            int peek = position;
            while (peek < endOffset && Character.isWhitespace(buffer.charAt(peek))) {
                peek++;
            }
            boolean isFunction = peek < endOffset && buffer.charAt(peek) == '(';

            if (isFunction) {
                tokenType = FUNCTION;
            } else if (Character.isUpperCase(word.charAt(0))) {
                tokenType = TYPE;
            } else {
                tokenType = VARIABLE;
            }
        }
    }

    private static boolean isMacroName(String word) {
        if (word.length() < 2) {
            return false;
        }
        boolean hasLetter = false;
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if (Character.isLetter(c)) {
                hasLetter = true;
                if (!Character.isUpperCase(c)) {
                    return false;
                }
            } else if (!Character.isDigit(c) && c != '_') {
                return false;
            }
        }
        return hasLetter;
    }

    private void scanPreprocessor() {
        position++; // '#'
        if (position < endOffset && isIdentifierStart(buffer.charAt(position))) {
            while (position < endOffset && isIdentifierPart(buffer.charAt(position))) {
                position++;
            }
        } else if (position < endOffset && Character.isDigit(buffer.charAt(position))) {
            while (position < endOffset && Character.isDigit(buffer.charAt(position))) {
                position++;
            }
        }
        tokenType = PREPROCESSOR;
    }

    private boolean scanOperator() {
        for (String operator : OPERATORS) {
            int length = operator.length();
            if (position + length <= endOffset
                    && buffer.subSequence(position, position + length).toString().equals(operator)) {
                position += length;
                return true;
            }
        }
        return false;
    }

    private static boolean isIdentifierStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    private static boolean isIdentifierPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    @NotNull
    @Override
    public CharSequence getBufferSequence() {
        return buffer;
    }

    @Override
    public int getBufferEnd() {
        return endOffset;
    }
}
