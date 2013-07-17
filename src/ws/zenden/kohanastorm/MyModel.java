package ws.zenden.kohanastorm;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public interface MyModel {
  String getPromptText();

  String getNotInMessage();
  String getNotFoundMessage();
  /** return null to hide checkbox panel */
  @Nullable String getCheckBoxName();

    interface Callback {
    public abstract void elementChosen(Object element);
    public abstract void onClose(MyPopup popup);
  }
  /**
   * @deprecated Mark mnemonic char with '&' ('&&' for mac if mnemonic char is 'N') in checkbox name instead
   */
  char getCheckBoxMnemonic();


  boolean loadInitialCheckBoxState();
  void saveInitialCheckBoxState(boolean state);

  ListCellRenderer getListCellRenderer();

  /**
   * Returns the list of names to show in the chooser.
   *
   * @param checkBoxState the current state of the chooser checkbox (for example, [x] Include non-project classes for Ctrl-N)
   * @return the names to show. All items in the returned array must be non-null.
   *
   */
  String[] getNames(boolean checkBoxState);
  Object[] getElementsByPattern(String userPattern);
  Object[] getElementsByName(String name, boolean checkBoxState, final String pattern);
  @Nullable
  String getElementName(Object element);

  @NotNull
  String[] getSeparators();

  @Nullable
  String getFullName(Object element);

  @Nullable
  String getHelpId();

  boolean willOpenEditor();
}