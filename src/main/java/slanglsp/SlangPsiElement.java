package slanglsp;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

public class SlangPsiElement extends ASTWrapperPsiElement {
    public SlangPsiElement(@NotNull ASTNode node) {
        super(node);
    }
}
