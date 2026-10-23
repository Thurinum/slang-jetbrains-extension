package slanglsp;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.features.navigation.LSPGotoDeclarationHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Ctrl+Click / Go to Declaration for Slang files.
 * <p>
 * {@code slangd} only implements {@code textDocument/definition}, so references navigate to their
 * definition. When the clicked identifier is already the declaration (or has no definition at all),
 * the other occurrences of the identifier are offered instead, matching C# "go to usages" behavior.
 */
public class SlangGotoDeclarationHandler implements GotoDeclarationHandler {

    @Override
    public PsiElement[] getGotoDeclarationTargets(@Nullable PsiElement sourceElement,
                                                  int offset,
                                                  Editor editor) {
        if (sourceElement == null) {
            return PsiElement.EMPTY_ARRAY;
        }
        PsiFile file = sourceElement.getContainingFile();
        if (!(file instanceof SlangFile) || file.getVirtualFile() == null) {
            return PsiElement.EMPTY_ARRAY;
        }

        PsiElement[] definitions = LSPGotoDeclarationHandler.getGotoDeclarationTargets(sourceElement, offset);
        if (definitions.length > 0 && !containsDeclarationAt(definitions, file.getVirtualFile(), offset)) {
            return definitions;
        }

        // The clicked identifier is the declaration itself (slangd resolves it to its own range) or
        // has no definition. Offer the other occurrences, like "go to usages" in C#.
        SlangOccurrences.Occurrence self = SlangOccurrences.identifierAt(file, offset);
        if (self == null) {
            return definitions;
        }
        Project project = file.getProject();
        List<SlangOccurrences.Occurrence> all = ReadAction.compute(
                () -> SlangOccurrences.find(project, self.name(), null));
        List<SlangOccurrences.Occurrence> others = new ArrayList<>(all.size());
        for (SlangOccurrences.Occurrence occurrence : all) {
            boolean isSelf = occurrence.file().equals(self.file())
                    && occurrence.startOffset() == self.startOffset();
            if (!isSelf) {
                others.add(occurrence);
            }
        }
        return SlangOccurrences.toPsiElements(project, others).toArray(PsiElement.EMPTY_ARRAY);
    }

    private static boolean containsDeclarationAt(PsiElement[] definitions, VirtualFile file, int offset) {
        for (PsiElement definition : definitions) {
            PsiFile definitionFile = definition.getContainingFile();
            if (definitionFile == null || !file.equals(definitionFile.getVirtualFile())) {
                continue;
            }
            TextRange range = definition.getTextRange();
            if (range != null && (range.containsOffset(offset) || range.getStartOffset() == offset)) {
                return true;
            }
        }
        return false;
    }
}
