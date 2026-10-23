package slanglsp;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * C#-style automatic brace insertion for Slang: typing {@code {}/{@code (}/{@code [} inserts the
 * matching closing character and keeps the caret in between. Typing a closing character that is
 * already present steps over it instead of duplicating it.
 * <p>
 * This works together with LSP4IJ's enter-between-braces handler (enabled in
 * {@link SlangLSPClientFeatures}), which formats the inserted pair as
 * <pre>
 * {
 *     |
 * }
 * </pre>
 * when Enter is pressed.
 */
public class SlangTypedHandler extends TypedHandlerDelegate {

    @Override
    public Result beforeCharTyped(char charTyped,
                                  @NotNull Project project,
                                  @NotNull Editor editor,
                                  @NotNull PsiFile file,
                                  @NotNull FileType fileType) {
        if (!(file instanceof SlangFile) || !CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET) {
            return Result.CONTINUE;
        }
        if (editor.getSelectionModel().hasSelection()) {
            return Result.CONTINUE;
        }

        Document document = editor.getDocument();
        int offset = editor.getCaretModel().getOffset();
        CharSequence chars = document.getCharsSequence();
        boolean atDocumentEnd = offset >= document.getTextLength();

        Character closer = closerFor(charTyped);
        if (closer != null) {
            if (!atDocumentEnd && chars.charAt(offset) == closer) {
                // The matching closer is already there: step over it.
                editor.getCaretModel().moveToOffset(offset + 1);
                return Result.STOP;
            }
            if (isInsideLiteral(file, chars, offset)) {
                return Result.CONTINUE;
            }
            document.insertString(offset, String.valueOf(charTyped) + closer);
            editor.getCaretModel().moveToOffset(offset + 1);
            if (charTyped == '(' || charTyped == '[') {
                // Our STOP prevents LSP4IJ's completion-trigger typed handler from running, so
                // schedule the auto popup ourselves (member/argument completion after ( or [).
                AutoPopupController.getInstance(project).scheduleAutoPopup(editor);
            }
            return Result.STOP;
        }

        // Typing a closing character that was inserted automatically steps over it.
        if (isCloser(charTyped) && !atDocumentEnd && chars.charAt(offset) == charTyped) {
            editor.getCaretModel().moveToOffset(offset + 1);
            return Result.STOP;
        }

        return Result.CONTINUE;
    }

    private static boolean isInsideLiteral(@NotNull PsiFile file, @NotNull CharSequence chars, int offset) {
        // Check just before the caret so typing at the very start does not count as inside.
        int probe = offset > 0 ? offset - 1 : offset;
        if (probe >= chars.length()) {
            return false;
        }
        return SlangOccurrences.tokenTypeAt(chars, probe) == SlangLexer.COMMENT
                || SlangOccurrences.tokenTypeAt(chars, probe) == SlangLexer.STRING;
    }

    private static Character closerFor(char c) {
        switch (c) {
            case '{':
                return '}';
            case '(':
                return ')';
            case '[':
                return ']';
            default:
                return null;
        }
    }

    private static boolean isCloser(char c) {
        return c == '}' || c == ')' || c == ']';
    }
}
