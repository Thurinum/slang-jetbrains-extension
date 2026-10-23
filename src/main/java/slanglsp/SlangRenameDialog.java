package slanglsp;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;

/**
 * Rename dialog for Slang identifiers. Lets the user choose between renaming in the current file
 * and across all Slang files of the project.
 */
class SlangRenameDialog extends DialogWrapper {

    private final String oldName;
    private final JBTextField nameField;
    private final JBCheckBox projectWide = new JBCheckBox("Search in all Slang files", true);

    SlangRenameDialog(@Nullable Project project, @NotNull String oldName) {
        super(project, true);
        this.oldName = oldName;
        this.nameField = new JBTextField(oldName);
        this.nameField.selectAll();
        setTitle("Rename '" + oldName + "'");
        init();
    }

    @Override
    protected @NotNull JComponent createCenterPanel() {
        return FormBuilder.createFormBuilder()
                .addLabeledComponent("New name:", nameField)
                .addComponent(projectWide)
                .getPanel();
    }

    @Override
    protected void doOKAction() {
        String newName = getNewName();
        if (newName.equals(oldName)) {
            super.doOKAction();
            return;
        }
        if (!SlangOccurrences.isValidIdentifier(newName)) {
            setErrorText("'" + newName + "' is not a valid Slang identifier.");
            return;
        }
        super.doOKAction();
    }

    @NotNull
    String getNewName() {
        return nameField.getText().trim();
    }

    boolean isProjectWide() {
        return projectWide.isSelected();
    }
}
