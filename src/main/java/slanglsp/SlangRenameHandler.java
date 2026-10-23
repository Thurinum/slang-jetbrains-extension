package slanglsp;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.rename.RenameHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rename for Slang files.
 * <p>
 * {@code slangd} does not implement {@code textDocument/rename} (see shader-slang/slang#6335), so
 * the plugin renames identifier occurrences itself. The lexer is used to only touch identifiers and
 * to skip comments and string literals.
 */
public class SlangRenameHandler implements RenameHandler {

    @Override
    public boolean isAvailableOnDataContext(@NotNull com.intellij.openapi.actionSystem.DataContext dataContext) {
        return findTarget(dataContext) != null;
    }

    @Override
    public void invoke(@NotNull Project project,
                       @Nullable Editor editor,
                       @NotNull PsiFile file,
                       @Nullable com.intellij.openapi.actionSystem.DataContext dataContext) {
        if (editor == null || !(file instanceof SlangFile)) {
            return;
        }
        SlangOccurrences.Occurrence target = SlangOccurrences.identifierAt(file, editor.getCaretModel().getOffset());
        if (target == null) {
            return;
        }
        SlangRenameDialog dialog = new SlangRenameDialog(project, target.name());
        if (!dialog.showAndGet()) {
            return;
        }
        rename(project, target, dialog.getNewName(), dialog.isProjectWide());
    }

    @Override
    public void invoke(@NotNull Project project,
                       PsiElement @NotNull [] elements,
                       @Nullable com.intellij.openapi.actionSystem.DataContext dataContext) {
        if (elements.length == 0) {
            return;
        }
        PsiFile file = elements[0].getContainingFile();
        Editor editor = dataContext != null
                ? com.intellij.openapi.actionSystem.CommonDataKeys.EDITOR.getData(dataContext)
                : null;
        if (file != null) {
            invoke(project, editor, file, dataContext);
        }
    }

    @Nullable
    private static SlangOccurrences.Occurrence findTarget(
            @NotNull com.intellij.openapi.actionSystem.DataContext dataContext) {
        Editor editor = com.intellij.openapi.actionSystem.CommonDataKeys.EDITOR.getData(dataContext);
        PsiFile file = com.intellij.openapi.actionSystem.CommonDataKeys.PSI_FILE.getData(dataContext);
        if (editor == null || !(file instanceof SlangFile)) {
            return null;
        }
        return SlangOccurrences.identifierAt(file, editor.getCaretModel().getOffset());
    }

    private static void rename(@NotNull Project project,
                               @NotNull SlangOccurrences.Occurrence target,
                               @NotNull String newName,
                               boolean projectWide) {
        List<SlangOccurrences.Occurrence> occurrences = ReadAction.compute(
                () -> SlangOccurrences.find(project, target.name(),
                        projectWide ? null : target.file()));
        if (occurrences.isEmpty()) {
            return;
        }

        Map<VirtualFile, List<SlangOccurrences.Occurrence>> byFile = new LinkedHashMap<>();
        for (SlangOccurrences.Occurrence occurrence : occurrences) {
            byFile.computeIfAbsent(occurrence.file(), file -> new ArrayList<>()).add(occurrence);
        }

        WriteCommandAction.runWriteCommandAction(project, "Rename '" + target.name() + "'", null, () -> {
            FileDocumentManager documentManager = FileDocumentManager.getInstance();
            PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);
            for (Map.Entry<VirtualFile, List<SlangOccurrences.Occurrence>> entry : byFile.entrySet()) {
                Document document = documentManager.getDocument(entry.getKey());
                if (document == null) {
                    continue;
                }
                List<SlangOccurrences.Occurrence> fileOccurrences = new ArrayList<>(entry.getValue());
                // Replace from the end so earlier offsets stay valid.
                fileOccurrences.sort(Comparator.comparingInt(SlangOccurrences.Occurrence::startOffset).reversed());
                for (SlangOccurrences.Occurrence occurrence : fileOccurrences) {
                    document.replaceString(occurrence.startOffset(), occurrence.endOffset(), newName);
                }
                psiDocumentManager.commitDocument(document);
            }
        });

        NotificationGroupManager.getInstance().getNotificationGroup("Slang LSP").createNotification(
                "Slang rename",
                "Renamed '" + target.name() + "' to '" + newName + "' in "
                        + occurrences.size() + " place(s) across " + byFile.size() + " file(s).",
                NotificationType.INFORMATION
        ).notify(project);
    }
}
