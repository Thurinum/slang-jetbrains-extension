package slanglsp;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import org.jetbrains.annotations.NotNull;

public class SlangFile extends PsiFileBase {
    public SlangFile(@NotNull FileViewProvider viewProvider) {
        super(viewProvider, SlangLanguage.INSTANCE);
    }

    @NotNull
    @Override
    public FileType getFileType() {
        return SlangFileType.INSTANCE;
    }
}
