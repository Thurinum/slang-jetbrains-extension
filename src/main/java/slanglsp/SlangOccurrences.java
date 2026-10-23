package slanglsp;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Lexical identifier search shared by go to declaration, rename and find usages.
 * <p>
 * {@code slangd} does not implement {@code textDocument/references} or {@code textDocument/rename},
 * so the plugin has to find occurrences itself. Identifiers are located with the Slang lexer, which
 * keeps matches out of comments and string literals.
 */
final class SlangOccurrences {

    /**
     * One identifier occurrence. Offsets are document offsets in {@link #file}.
     */
    record Occurrence(@NotNull VirtualFile file, int startOffset, int endOffset, @NotNull String name) {
    }

    private SlangOccurrences() {
    }

    static boolean isIdentifierToken(@Nullable IElementType type) {
        return type == SlangLexer.TYPE || type == SlangLexer.FUNCTION || type == SlangLexer.VARIABLE;
    }

    /**
     * Returns the identifier under the given offset, or {@code null} if the offset is not on an
     * identifier (keyword, literal, comment, whitespace, ...).
     */
    static @Nullable Occurrence identifierAt(@NotNull PsiFile file, int offset) {
        VirtualFile virtualFile = file.getVirtualFile();
        if (virtualFile == null) {
            return null;
        }
        Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
        if (document == null) {
            return null;
        }
        CharSequence text = document.getImmutableCharSequence();
        SlangLexer lexer = new SlangLexer();
        lexer.start(text);
        while (lexer.getTokenType() != null) {
            int start = lexer.getTokenStart();
            int end = lexer.getTokenEnd();
            if (start <= offset && offset < end && isIdentifierToken(lexer.getTokenType())) {
                return new Occurrence(virtualFile, start, end, text.subSequence(start, end).toString());
            }
            if (start > offset) {
                break;
            }
            lexer.advance();
        }
        return null;
    }

    /**
     * Finds all occurrences of {@code name} in Slang files.
     *
     * @param restrictTo when not {@code null}, only this file is searched
     */
    static @NotNull List<Occurrence> find(@NotNull Project project,
                                          @NotNull String name,
                                          @Nullable VirtualFile restrictTo) {
        Collection<VirtualFile> files = restrictTo != null
                ? List.of(restrictTo)
                : FileTypeIndex.getFiles(SlangFileType.INSTANCE, GlobalSearchScope.projectScope(project));

        List<Occurrence> occurrences = new ArrayList<>();
        FileDocumentManager documentManager = FileDocumentManager.getInstance();
        for (VirtualFile file : files) {
            Document document = documentManager.getDocument(file);
            if (document == null) {
                continue;
            }
            CharSequence text = document.getImmutableCharSequence();
            SlangLexer lexer = new SlangLexer();
            lexer.start(text);
            while (lexer.getTokenType() != null) {
                int start = lexer.getTokenStart();
                int end = lexer.getTokenEnd();
                if (isIdentifierToken(lexer.getTokenType())
                        && end - start == name.length()
                        && text.subSequence(start, end).toString().equals(name)) {
                    occurrences.add(new Occurrence(file, start, end, name));
                }
                lexer.advance();
            }
        }
        return occurrences;
    }

    static @NotNull List<PsiElement> toPsiElements(@NotNull Project project,
                                                   @NotNull List<Occurrence> occurrences) {
        List<PsiElement> elements = new ArrayList<>(occurrences.size());
        PsiManager psiManager = PsiManager.getInstance(project);
        for (Occurrence occurrence : occurrences) {
            PsiFile file = psiManager.findFile(occurrence.file());
            if (file == null) {
                continue;
            }
            PsiElement element = file.findElementAt(occurrence.startOffset());
            if (element != null) {
                elements.add(element);
            }
        }
        return elements;
    }
}
