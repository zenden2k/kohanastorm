package ws.zenden.kohanastorm;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ComponentPopupBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.psi.PsiElement;
import com.intellij.ui.ScreenUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MyPopup extends MyPopupBase{
  private static final Key<MyPopup> CHOOSE_BY_NAME_POPUP_IN_PROJECT_KEY = new Key<MyPopup>("MyPopup");
  private Component myOldFocusOwner = null;
  private boolean myShowListForEmptyPattern = false;
  private boolean myMayRequestCurrentWindow;

  protected MyPopup(final Project project, final MyModel model, /*ChooseByNameItemProvider provider,*/ final MyPopup oldPopup,
                            @Nullable final String predefinedText, boolean mayRequestOpenInCurrentWundow, int initialIndex) {
    super(project, model, /*provider, */oldPopup != null ? oldPopup.getEnteredText() : predefinedText, initialIndex);
    if (oldPopup == null && predefinedText != null) {
      setPreselectInitialText(true);
    }
    if (oldPopup != null) { //inherit old focus owner
      //myOldFocusOwner = oldPopup.myPreviouslyFocusedComponent;
    }
    myMayRequestCurrentWindow = mayRequestOpenInCurrentWundow;
  }

  public String getEnteredText() {
    return myTextField.getText();
  }

  public int getSelectedIndex() {
    return myList.getSelectedIndex();
  }

  protected void initUI(final MyModel.Callback callback, final ModalityState modalityState, boolean allowMultipleSelection) {
    super.initUI(callback, /*callback, */modalityState, allowMultipleSelection);
    //LaterInvocator.enterModal(myTextFieldPanel);
    if (myInitialText != null) {
      rebuildList(myInitialIndex, 0, null, ModalityState.current(), null);
    }
    if (myOldFocusOwner != null){
      myPreviouslyFocusedComponent = myOldFocusOwner;
      myOldFocusOwner = null;
    }
  }

  @Override
  public boolean isOpenInCurrentWindowRequested() {
    return super.isOpenInCurrentWindowRequested() && myMayRequestCurrentWindow;
  }

  protected boolean isCheckboxVisible() {
    return true;
  }

  protected boolean isShowListForEmptyPattern(){
    return myShowListForEmptyPattern;
  }

  public void setShowListForEmptyPattern(boolean showListForEmptyPattern) {
    myShowListForEmptyPattern = showListForEmptyPattern;
  }

  protected boolean isCloseByFocusLost(){
    return true;
  }

  protected void showList() {
    final JLayeredPane layeredPane = myTextField.getRootPane().getLayeredPane();
    Rectangle bounds = new Rectangle(myTextFieldPanel.getLocationOnScreen(), myTextField.getSize());
    bounds.y += myTextFieldPanel.getHeight() + (SystemInfo.isMac ? 3 : 1);

    final Dimension preferredScrollPaneSize = myListScrollPane.getPreferredSize();
    if (myList.getModel().getSize() == 0) {
      preferredScrollPaneSize.height = UIManager.getFont("Label.font").getSize();
    }

    preferredScrollPaneSize.width = Math.max(myTextFieldPanel.getWidth(), preferredScrollPaneSize.width);

    Rectangle prefferedBounds = new Rectangle(bounds.x, bounds.y, preferredScrollPaneSize.width, preferredScrollPaneSize.height);
    Rectangle original = new Rectangle(prefferedBounds);

    ScreenUtil.fitToScreen(prefferedBounds);
    if (original.width > prefferedBounds.width) {
      int height = myListScrollPane.getHorizontalScrollBar().getPreferredSize().height;
      prefferedBounds.height += height;
    }

    myListScrollPane.setVisible(true);
    myListScrollPane.setBorder(null);
    String adText = myMayRequestCurrentWindow ? "Press " + KeymapUtil.getKeystrokeText(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.SHIFT_MASK)) + " to open in current window" : null;
    if (myDropdownPopup == null) {
      ComponentPopupBuilder builder = JBPopupFactory.getInstance().createComponentPopupBuilder(myListScrollPane, myListScrollPane);
      builder.setFocusable(false).setRequestFocus(false).setCancelKeyEnabled(false).setFocusOwners(new JComponent[] {myTextField}).setBelongsToGlobalPopupStack(false)
        ./*setForceHeavyweight(true).*/setModalContext(false).setAdText(adText).setMayBeParent(true);
      builder.setCancelCallback(new Computable<Boolean>() {
        //@Override
        public Boolean compute() {
          return Boolean.TRUE;
        }
      });
      myDropdownPopup = builder.createPopup();
      myDropdownPopup.setLocation(prefferedBounds.getLocation());
      myDropdownPopup.setSize(prefferedBounds.getSize());
      myDropdownPopup.show(layeredPane);
    } else {
      myDropdownPopup.setLocation(prefferedBounds.getLocation());
      myDropdownPopup.setSize(prefferedBounds.getSize());
    }
  }

  protected void hideList() {
    if (myDropdownPopup != null) {
      myDropdownPopup.cancel();
      myDropdownPopup = null;
    }
  }

  protected void close(final boolean isOk) {
    if (checkDisposed()){
      return;
    }
      if ( isOk) {


     final List<Object> chosenElements = getChosenElements();
      if (chosenElements != null) {
           for (Object element : chosenElements) {
               if ( element != null ) {
                    myActionListener.elementChosen(element);
               }

          }
        //myActionListener.elementChosen();
      } else {
        return;
      }
      }

    setDisposed(true);
    myAlarm.cancelAllRequests();
    myProject.putUserData(CHOOSE_BY_NAME_POPUP_IN_PROJECT_KEY, null);

    //LaterInvocator.leaveModal(myTextFieldPanel);

    cleanupUI(isOk);
    myActionListener.onClose (this);
  }
 /*
  @Nullable
  public static MyPopup getActivePopup(@NotNull final Project project) {
    return CHOOSE_BY_NAME_POPUP_IN_PROJECT_KEY.get(project);
  }   */

  private void cleanupUI(boolean ok) {
    if (myTextPopup != null) {
      if (ok) {
        myTextPopup.closeOk(null);
      } else {
        myTextPopup.cancel();
      }
      myTextPopup = null;
    }

    if (myDropdownPopup != null) {
      if (ok) {
        myDropdownPopup.closeOk(null);
      } else {
        myDropdownPopup.cancel();
      }
      myDropdownPopup = null;
    }
  }

 /* public static MyPopup createPopup(final Project project, final MyModel model/*, final PsiElement context) {
    return createPopup(project, model, /*new DefaultChooseByNameItemProvider(context),* null);
  }  */

  public static MyPopup createPopup(final Project project, final MyModel model, final PsiElement context,
                                              @Nullable final String predefinedText) {
    return createPopup(project, model, /*new DefaultChooseByNameItemProvider(context),*/ predefinedText, false, 0);
  }

  public static MyPopup createPopup(final Project project, final MyModel model, final PsiElement context,
                                              @Nullable final String predefinedText,
                                              boolean mayRequestOpenInCurrentWindow, final int initialIndex) {
     return createPopup(project,model,/*new DefaultChooseByNameItemProvider(context),*/predefinedText,mayRequestOpenInCurrentWindow,initialIndex);
  }

  public static MyPopup createPopup(final Project project, final MyModel model/*, final ChooseByNameItemProvider provider*/) {
    return createPopup(project, model /*provider*/, null);
  }

  public static MyPopup createPopup(final Project project, final MyModel model/*, final ChooseByNameItemProvider provider*/,
                                              @Nullable final String predefinedText) {
    return createPopup(project, model, /*provider,*/ predefinedText, false, 0);
  }

  public static MyPopup createPopup(final Project project, final MyModel model/*, final ChooseByNameItemProvider provider*/,
                                              @Nullable final String predefinedText,
                                              boolean mayRequestOpenInCurrentWindow, final int initialIndex) {
    final MyPopup oldPopup = project.getUserData(CHOOSE_BY_NAME_POPUP_IN_PROJECT_KEY);
    if (oldPopup != null) {
      oldPopup.close(false);
    }
    MyPopup newPopup = new MyPopup(project, model, /*provider, */oldPopup, predefinedText, mayRequestOpenInCurrentWindow,
                                                       initialIndex);

    project.putUserData(CHOOSE_BY_NAME_POPUP_IN_PROJECT_KEY, newPopup);
    return newPopup;
  }

  private static final Pattern patternToDetectLinesAndColumns = Pattern.compile("(.+)(?::|@|,|#)(\\d+)?(?:(?:\\D)(\\d+)?)?");
  private static final Pattern patternToDetectAnonymousClasses = Pattern.compile("([\\.\\w]+)((\\$\\d)*(\\$)?)");

  public String transformPattern(String pattern) {
      return pattern;
  }

  public int getLinePosition() {
    return getLineOrColumn(true);
  }

  private int getLineOrColumn(final boolean line) {
    final Matcher matcher = patternToDetectLinesAndColumns.matcher(getEnteredText());
    if (matcher.matches()) {
      final int groupNumber = line ? 2:3;
      try {
        if(groupNumber <= matcher.groupCount()) {
          final String group = matcher.group(groupNumber);
          if (group != null) return Integer.parseInt(group) - 1;
        }
        if (!line && getLineOrColumn(true) != -1) return 0;
      }
      catch (NumberFormatException ignored) {}
    }

    return -1;
  }

  @Nullable
  public String getPathToAnonymous() {
    final Matcher matcher = patternToDetectAnonymousClasses.matcher(getEnteredText());
    if (matcher.matches()) {
      String path = matcher.group(2);
      if (path != null) {
        path = path.trim();
        if (path.endsWith("$")) {
          path = path.substring(0, path.length() - 2);
        }
        if (!path.isEmpty()) return path;
      }
    }

    return null;
  }

  public int getColumnPosition() {
    return getLineOrColumn(false);
  }
}
