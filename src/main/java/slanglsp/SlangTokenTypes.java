package slanglsp;

import com.intellij.psi.tree.IElementType;

public final class SlangTokenTypes {
    public static final IElementType L_CURLY = new IElementType("{", SlangLanguage.INSTANCE);
    public static final IElementType R_CURLY = new IElementType("}", SlangLanguage.INSTANCE);
    public static final IElementType L_PARENTHESIS = new IElementType("(", SlangLanguage.INSTANCE);
    public static final IElementType R_PARENTHESIS = new IElementType(")", SlangLanguage.INSTANCE);
    public static final IElementType L_BRACKET = new IElementType("[", SlangLanguage.INSTANCE);
    public static final IElementType R_BRACKET = new IElementType("]", SlangLanguage.INSTANCE);

    private SlangTokenTypes() {
    }
}
