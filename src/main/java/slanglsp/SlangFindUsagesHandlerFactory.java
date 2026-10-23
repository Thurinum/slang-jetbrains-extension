package slanglsp;

import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesHandlerFactory;
import com.intellij.find.findUsages.FindUsagesOptions;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Find Usages for Slang identifiers.
 * <p>
 * {@code slangd} does not implement {@code textDocument/references}, so LSP4IJ's usage search
 * cannot return anything. This factory searches identifier occurrences itself using the Slang
 * lexer, which keeps comments and string literals out of the results.
 */
public class SlangFindUsagesHandlerFactory extends FindUsagesHandlerFactory {

    @Override
    public boolean canFindUsages(@NotNull PsiElement element) {
        PsiFile file = element.getContainingFile();
        if (!(file instanceof SlangFile)) {
            return false;
        }
        return ReadAction.compute(
                () -> SlangOccurrences.identifierAt(file, element.getTextOffset())) != null;
    }

    @Override
    public @Nullable FindUsagesHandler createFindUsagesHandler(@NotNull PsiElement element,
                                                               boolean forHighlightUsages) {
        return canFindUsages(element) ? new SlangFindUsagesHandler(element) : null;
    }

    /**
     * Returns all lexical occurrences of the identifier under the caret as usages.
     */
    private static final class SlangFindUsagesHandler extends FindUsagesHandler {

        SlangFindUsagesHandler(@NotNull PsiElement element) {
            super(element);
        }

        @Override
        public boolean processElementUsages(@NotNull PsiElement element,
                                            @NotNull Processor<? super UsageInfo> processor,
                                            @NotNull FindUsagesOptions options) {
            PsiFile file = element.getContainingFile();
            if (!(file instanceof SlangFile)) {
                return false;
            }
            SlangOccurrences.Occurrence target = SlangOccurrences.identifierAt(file, element.getTextOffset());
            if (target == null) {
                return false;
            }
            Project project = element.getProject();
            SearchScope searchScope = options.searchScope;
            GlobalSearchScope scope = searchScope instanceof GlobalSearchScope
                    ? (GlobalSearchScope) searchScope
                    : GlobalSearchScope.projectScope(project);

            List<SlangOccurrences.Occurrence> occurrences = ReadAction.compute(
                    () -> SlangOccurrences.findInScope(project, target.name(), scope));
            PsiManager psiManager = PsiManager.getInstance(project);
            for (SlangOccurrences.Occurrence occurrence : occurrences) {
                PsiFile occurrenceFile = psiManager.findFile(occurrence.file());
                if (occurrenceFile == null) {
                    continue;
                }
                UsageInfo usage = new UsageInfo(occurrenceFile, occurrence.startOffset(), occurrence.endOffset());
                if (!processor.process(usage)) {
                    break;
                }
            }
            return true;
        }
    }
}
