package slanglsp;

import com.intellij.lang.BracePair;
import com.intellij.lang.PairedBraceMatcher;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides C#-style automatic brace insertion/matching for Slang.
 * <p>
 * Registering a {@link PairedBraceMatcher} is what enables the platform to:
 * <ul>
 *     <li>insert the matching closing brace when an opening one is typed,</li>
 *     <li>highlight the matching brace,</li>
 *     <li>type over an already-inserted closing brace instead of duplicating it, and</li>
 *     <li>re-indent the closing brace when it is typed.</li>
 * </ul>
 */
public class SlangBraceMatcher implements PairedBraceMatcher {
    private static final BracePair[] PAIRS = new BracePair[]{
            new BracePair(SlangTokenTypes.L_CURLY, SlangTokenTypes.R_CURLY, true),
            new BracePair(SlangTokenTypes.L_PARENTHESIS, SlangTokenTypes.R_PARENTHESIS, false),
            new BracePair(SlangTokenTypes.L_BRACKET, SlangTokenTypes.R_BRACKET, false),
    };

    @Override
    public BracePair[] getPairs() {
        return PAIRS;
    }

    @Override
    public boolean isPairedBracesAllowedBeforeType(@NotNull IElementType lBraceType, @Nullable IElementType contextType) {
        return true;
    }

    @Override
    public int getCodeConstructStart(PsiFile file, int openingBraceOffset) {
        return openingBraceOffset;
    }
}
