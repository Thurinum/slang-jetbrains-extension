package slanglsp;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.editorActions.BackspaceHandlerDelegate;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import org.jetbrains.annotations.NotNull;

/**
 * Smart backspace in leading indentation: instead of deleting one space at a time, backspace
 * removes the indentation back to the previous indent stop (C#/Rider behavior).
 */
public class SlangSmartBackspaceHandler extends BackspaceHandlerDelegate {

    @Override
    public void beforeCharDeleted(char charDeleted, @NotNull PsiFile file, @NotNull Editor editor) {
        // No-op
    }

    @Override
    public boolean charDeleted(char charDeleted, @NotNull PsiFile file, @NotNull Editor editor) {
        if (!(file instanceof SlangFile) || (charDeleted != ' ' && charDeleted != '\t')) {
            return false;
        }

        Document document = editor.getDocument();
        int offset = editor.getCaretModel().getOffset();
        int lineStart = document.getLineStartOffset(document.getLineNumber(offset));
        if (offset <= lineStart) {
            return false;
        }

        CharSequence chars = document.getCharsSequence();
        for (int i = lineStart; i < offset; i++) {
            char c = chars.charAt(i);
            if (c != ' ' && c != '\t') {
                // Caret is not in the leading indentation.
                return false;
            }
        }

        CommonCodeStyleSettings.IndentOptions options = CodeStyle.getIndentOptions(file);
        int target;
        if (options != null && options.USE_TAB_CHARACTER) {
            target = lineStart;
            for (int i = offset - 1; i >= lineStart; i--) {
                if (chars.charAt(i) == '\t') {
                    target = i;
                    break;
                }
            }
        } else {
            int indentSize = options != null && options.INDENT_SIZE > 0 ? options.INDENT_SIZE : 4;
            int column = offset - lineStart;
            // One character has already been deleted by the platform. Jump to the previous
            // indent stop, e.g. column 7 -> 4, column 5 -> 4, column 3 -> 0.
            target = lineStart + ((column - 1) / indentSize) * indentSize;
            if (target < lineStart) {
                target = lineStart;
            }
        }

        if (target >= offset) {
            return false;
        }
        document.deleteString(target, offset);
        editor.getCaretModel().moveToOffset(target);
        return true;
    }
}
