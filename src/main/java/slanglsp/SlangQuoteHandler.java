package slanglsp;

import com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler;

/**
 * Auto-closes string/char literals recorded by {@link SlangLexer#STRING}.
 */
public class SlangQuoteHandler extends SimpleTokenSetQuoteHandler {
    public SlangQuoteHandler() {
        super(SlangLexer.STRING);
    }
}
