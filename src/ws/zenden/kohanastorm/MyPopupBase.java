package ws.zenden.kohanastorm;

import com.intellij.Patches;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.CopyReferenceAction;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.MnemonicHelper;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.ListScrollingUtil;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBList;
import com.intellij.ui.popup.PopupOwner;
//import com.intellij.ui.popup.PopupPositionManager;
import com.intellij.ui.popup.PopupUpdateProcessor;
import com.intellij.util.Alarm;
import com.intellij.util.diff.Diff;
//import com.intellij.util.diff.FilesTooBigForDiffException;
//import com.intellij.util.text.Matcher;
//import com.intellij.util.text.MatcherHolder;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.text.DefaultEditorKit;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

public abstract class MyPopupBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.util.gotoByName.ChooseByNameBase");

  protected final Project myProject;
  protected final MyModel myModel;
    protected  MyModel.Callback myActionListener = null;
 // protected ChooseByNameItemProvider myProvider;
  protected final String myInitialText;
  private boolean myPreselectInitialText;
  private boolean mySearchInAnyPlace = false;

  protected Component myPreviouslyFocusedComponent;

  protected JPanelProvider myTextFieldPanel;// Located in the layered pane
  protected MyTextField myTextField;
  private JPanel myCardContainer;
  private CardLayout myCard;
  protected JCheckBox myCheckBox;
  /**
   * the tool area of the popup, it is just after card box
   */
  private JComponent myToolArea;

  protected JScrollPane myListScrollPane; // Located in the layered pane
  protected JList myList;
  private DefaultListModel myListModel;
  private List<Pair<String, Integer>> myHistory;
  private List<Pair<String, Integer>> myFuture;



  protected final Alarm myAlarm = new Alarm();

  private final ListUpdater myListUpdater = new ListUpdater();

  private volatile boolean myListIsUpToDate = false;
  private boolean myDisposedFlag = false;
  private ActionCallback myPosponedOkAction;

  private final String[][] myNames = new String[2][];
  private CalcElementsThread myCalcElementsThread;
  private static int VISIBLE_LIST_SIZE_LIMIT = 10;
  private static final int MAXIMUM_LIST_SIZE_LIMIT = 30;
  private int myMaximumListSizeLimit = MAXIMUM_LIST_SIZE_LIMIT;
  @NonNls private static final String NOT_FOUND_IN_PROJECT_CARD = "syslib";
  @NonNls private static final String NOT_FOUND_CARD = "nfound";
  @NonNls private static final String CHECK_BOX_CARD = "chkbox";
  @NonNls private static final String SEARCHING_CARD = "searching";
  private static final int REBUILD_DELAY = 300;

  private final Alarm myHideAlarm = new Alarm();
  private boolean myShowListAfterCompletionKeyStroke = false;
  protected JBPopup myTextPopup;
  protected JBPopup myDropdownPopup;

  private boolean myClosedByShiftEnter = false;
  protected final int myInitialIndex;
  private String myFindUsagesTitle;

  public boolean checkDisposed() {
    if (myDisposedFlag && myPosponedOkAction != null && !myPosponedOkAction.isProcessed()) {
      myPosponedOkAction.setRejected();
    }

    return myDisposedFlag;
  }

  public void setDisposed(boolean disposedFlag) {
    myDisposedFlag = disposedFlag;
  }

  /**
   * @param initialText initial text which will be in the lookup text field
   * @param context
   */
  protected MyPopupBase(Project project, MyModel model, String initialText, PsiElement context) {
    this(project, model,/* new DefaultChooseByNameItemProvider(context),*/ initialText, 0);
  }

  @SuppressWarnings("UnusedDeclaration") // Used in MPS
  protected MyPopupBase(Project project, MyModel model, /*ChooseByNameItemProvider provider,*/ String initialText) {
    this(project, model, /*provider,*/ initialText, 0);
  }

  /**
   * @param initialText  initial text which will be in the lookup text field
   * @param initialIndex
   */
  protected MyPopupBase(Project project, MyModel model, /*ChooseByNameItemProvider provider,*/ String initialText, final int initialIndex) {
    myProject = project;
    myModel = model;
    myInitialText = initialText;
   // myProvider = provider;
    myInitialIndex = initialIndex;
  }

  public boolean isPreselectInitialText() {
    return myPreselectInitialText;
  }

  public void setPreselectInitialText(boolean preselectInitialText) {
    myPreselectInitialText = preselectInitialText;
  }

  public void setShowListAfterCompletionKeyStroke(boolean showListAfterCompletionKeyStroke) {
    myShowListAfterCompletionKeyStroke = showListAfterCompletionKeyStroke;
  }

  public boolean isSearchInAnyPlace() {
    return mySearchInAnyPlace;
  }

  public void setSearchInAnyPlace(boolean searchInAnyPlace) {
    mySearchInAnyPlace = searchInAnyPlace;
  }

  public boolean isClosedByShiftEnter() {
    return myClosedByShiftEnter;
  }

  public boolean isOpenInCurrentWindowRequested() {
    return isClosedByShiftEnter();
  }

  /**
   * Set tool area. The method may be called only before invoke.
   *
   * @param toolArea a tool area component
   */
  public void setToolArea(JComponent toolArea) {
    if (myCard != null) {
      throw new IllegalStateException("Tool area is modifiable only before invoke()");
    }
    myToolArea = toolArea;
  }

  public void setFindUsagesTitle(String findUsagesTitle) {
    myFindUsagesTitle = findUsagesTitle;
  }

  public void invoke(MyModel.Callback callback,/*final ChooseByNamePopupComponent.Callback callback,   */
                     final ModalityState modalityState,
                     boolean allowMultipleSelection) {
    initUI(callback, modalityState, allowMultipleSelection);
  }

  public MyModel getModel() {
    return myModel;
  }

  public class JPanelProvider extends JPanel implements DataProvider {
    private JBPopup myHint = null;
    private boolean myFocusRequested = false;

    JPanelProvider() {
    }

    public Object getData(String dataId) {
      if (PlatformDataKeys.HELP_ID.is(dataId)) {
        return myModel.getHelpId();
      }
      if (!myListIsUpToDate) {
        return null;
      }
      if (LangDataKeys.PSI_ELEMENT.is(dataId)) {
        Object element = getChosenElement();

        if (element instanceof PsiElement) {
          return element;
        }

        if (element instanceof DataProvider) {
          return ((DataProvider)element).getData(dataId);
        }
      }
      else if (LangDataKeys.PSI_ELEMENT_ARRAY.is(dataId)) {
        final List<Object> chosenElements = getChosenElements();
        if (chosenElements != null) {
          List<PsiElement> result = new ArrayList<PsiElement>();
          for (Object element : chosenElements) {
            if (element instanceof PsiElement) {
              result.add((PsiElement)element);
            }
          }
          return PsiUtilBase.toPsiElementArray(result);
        }
      }
      else if (PlatformDataKeys.DOMINANT_HINT_AREA_RECTANGLE.is(dataId)) {
        return getBounds();
      }
      return null;
    }

    public void registerHint(JBPopup h) {
      if (myHint != null && myHint.isVisible() && myHint != h) {
        myHint.cancel();
      }
      myHint = h;
    }

    public boolean focusRequested() {
      boolean focusRequested = myFocusRequested;

      myFocusRequested = false;

      return focusRequested;
    }

    public void requestFocus() {
      myFocusRequested = true;
    }

    public void unregisterHint() {
      myHint = null;
    }

    public void hideHint() {
      if (myHint != null) {
        myHint.cancel();
      }
    }

    public JBPopup getHint() {
      return myHint;
    }

    public void updateHint(PsiElement element) {
      if (myHint == null || !myHint.isVisible()) return;
      final PopupUpdateProcessor updateProcessor = myHint.getUserData(PopupUpdateProcessor.class);
      if (updateProcessor != null) {
        updateProcessor.updatePopup(element);
      }
    }

    public void repositionHint() {
      if (myHint == null || !myHint.isVisible()) return;
      //PopupPositionManager.positionPopupInBestPosition(myHint, null, null);
    }
  }

  /**
   * @param modalityState          - if not null rebuilds list in given {@link ModalityState}
   * @param allowMultipleSelection
   */
  protected void initUI(MyModel.Callback callback,/*final ChooseByNamePopupComponent.Callback callback, */
                        final ModalityState modalityState,
                        boolean allowMultipleSelection) {
    myPreviouslyFocusedComponent = WindowManagerEx.getInstanceEx().getFocusedComponent(myProject);

    myActionListener = callback;
    myTextFieldPanel = new JPanelProvider();
    myTextFieldPanel.setLayout(new BoxLayout(myTextFieldPanel, BoxLayout.Y_AXIS));

    final JPanel hBox = new JPanel();
    hBox.setLayout(new BoxLayout(hBox, BoxLayout.X_AXIS));

    JPanel caption2Tools = new JPanel(new BorderLayout());

    if (myModel.getPromptText() != null) {
      JLabel label = new JLabel(myModel.getPromptText());
      label.setFont(UIUtil.getLabelFont().deriveFont(Font.BOLD));
      caption2Tools.add(label, BorderLayout.WEST);
    }

    caption2Tools.add(hBox, BorderLayout.EAST);

    myCard = new CardLayout();
    myCardContainer = new JPanel(myCard);

    final String checkBoxName = myModel.getCheckBoxName();
    myCheckBox = new JCheckBox(checkBoxName != null ? checkBoxName : "");
    myCheckBox.setAlignmentX(SwingConstants.RIGHT);

    if (!SystemInfo.isMac) {
      myCheckBox.setBorder(null);
    }

   // myCheckBox.setSelected(myModel.loadInitialCheckBoxState());

    /*if (checkBoxName == null)*/ myCheckBox.setVisible(false);

    addCard(myCheckBox, CHECK_BOX_CARD);

    addCard(new JLabel(myModel.getNotInMessage()), NOT_FOUND_IN_PROJECT_CARD);
    addCard(new JLabel(IdeBundle.message("label.choosebyname.no.matches.found")), NOT_FOUND_CARD);
    JPanel searching = new JPanel(new BorderLayout(5, 0));
    searching.add(new AsyncProcessIcon("searching"), BorderLayout.WEST);
   // searching.add(new HintLabel(IdeBundle.message("label.choosebyname.searching")), BorderLayout.CENTER);
    addCard(searching, SEARCHING_CARD);
    myCard.show(myCardContainer, CHECK_BOX_CARD);

    if (isCheckboxVisible()) {
      hBox.add(myCardContainer);
    }


    final DefaultActionGroup group = new DefaultActionGroup();
   /* group.add(new ShowFindUsagesAction(){
      @Override
      public PsiElement[] getElements() {
        if (myListModel == null) return PsiElement.EMPTY_ARRAY;
        final Object[] objects = myListModel.toArray();
        final List<PsiElement> psiElements = new ArrayList<PsiElement>();
        for (Object object : objects) {
          if (object instanceof PsiElement) {
            psiElements.add((PsiElement)object);
          }
          else if (object instanceof DataProvider) {
            final PsiElement psi = LangDataKeys.PSI_ELEMENT.getData((DataProvider)object);
            if (psi != null) {
              psiElements.add(psi);
            }
          }

        }
        return psiElements.toArray(new PsiElement[psiElements.size()]);
      }
    });     */
    final ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true);
    actionToolbar.setLayoutPolicy(ActionToolbar.NOWRAP_LAYOUT_POLICY);
    final JComponent toolbarComponent = actionToolbar.getComponent();
    toolbarComponent.setBorder(null);

    hBox.add(toolbarComponent);

    if (myToolArea != null) {
      hBox.add(myToolArea);
    }
    myTextFieldPanel.add(caption2Tools);

    myHistory = new ArrayList<Pair<String, Integer>>();
    myFuture = new ArrayList<Pair<String, Integer>>();
    myTextField = new MyTextField();
    myTextField.setText(myInitialText);
    if (myPreselectInitialText) {
      myTextField.select(0, myInitialText.length());
    }

    final ActionMap actionMap = new ActionMap();
    actionMap.setParent(myTextField.getActionMap());
    actionMap.put(DefaultEditorKit.copyAction, new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        if (myTextField.getSelectedText() != null) {
          actionMap.getParent().get(DefaultEditorKit.copyAction).actionPerformed(e);
          return;
        }
        final Object chosenElement = getChosenElement();
        if (chosenElement instanceof PsiElement) {
          CopyReferenceAction.doCopy((PsiElement)chosenElement, myProject);
        }
      }
    });
    myTextField.setActionMap(actionMap);

    myTextFieldPanel.add(myTextField);
    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    Font editorFont = new Font(scheme.getEditorFontName(), Font.PLAIN, scheme.getEditorFontSize());
    myTextField.setFont(editorFont);

    if (isCloseByFocusLost()) {
      myTextField.addFocusListener(new FocusAdapter() {
        public void focusLost(final FocusEvent e) {
          myHideAlarm.addRequest(new Runnable() {
            public void run() {
              JBPopup popup = JBPopupFactory.getInstance().getChildFocusedPopup(e.getComponent());
              if (popup != null) {
                popup.addListener(new JBPopupListener.Adapter() {
                  @Override
                  public void onClosed(LightweightWindowEvent event) {
                    if (event.isOk()) {
                      hideHint();
                    }
                  }
                });
              }
              else {
                Component oppositeComponent = e.getOppositeComponent();
                if (oppositeComponent != null && !(oppositeComponent instanceof JFrame) &&
                    myList.isShowing() &&
                    (oppositeComponent == myList || SwingUtilities.isDescendingFrom(myList, oppositeComponent))) {
                  return;
                }
                hideHint();
              }
            }
                                 }, 200);
        }
      });
    }

    myCheckBox.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        rebuildList(false);
      }
    });
    myCheckBox.setFocusable(false);

    myTextField.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(DocumentEvent e) {
        //clearPosponedOkAction(false);
        rebuildList(false);
      }
    });

    final Set<KeyStroke> upShortcuts = getShortcuts(IdeActions.ACTION_EDITOR_MOVE_CARET_UP);
    final Set<KeyStroke> downShortcuts = getShortcuts(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN);
    myTextField.addKeyListener(new KeyAdapter() {
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER && (e.getModifiers() & InputEvent.SHIFT_MASK) != 0) {
          myClosedByShiftEnter = true;
          close(true);
        }
        if (!myListScrollPane.isVisible()) {
          return;
        }
        final int keyCode;

        // Add support for user-defined 'caret up/down' shortcuts.
        KeyStroke stroke = KeyStroke.getKeyStrokeForEvent(e);
        if (upShortcuts.contains(stroke)) {
          keyCode = KeyEvent.VK_UP;
        }
        else if (downShortcuts.contains(stroke)) {
          keyCode = KeyEvent.VK_DOWN;
        }
        else {
          keyCode = e.getKeyCode();
        }
        switch (keyCode) {
          case KeyEvent.VK_DOWN:
            ListScrollingUtil.moveDown(myList, e.getModifiersEx());
            break;
          case KeyEvent.VK_UP:
            ListScrollingUtil.moveUp(myList, e.getModifiersEx());
            break;
          case KeyEvent.VK_PAGE_UP:
            ListScrollingUtil.movePageUp(myList);
            break;
          case KeyEvent.VK_PAGE_DOWN:
            ListScrollingUtil.movePageDown(myList);
            break;
          case KeyEvent.VK_TAB:
            close(true);
            break;
          case KeyEvent.VK_ENTER:
            if (myList.getSelectedValue() == EXTRA_ELEM) {
              myMaximumListSizeLimit += MAXIMUM_LIST_SIZE_LIMIT;
              rebuildList(myList.getSelectedIndex(), REBUILD_DELAY, null, ModalityState.current(), e);
              e.consume();
            }
            break;
        }
      }
    });

    myTextField.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent actionEvent) {
        doClose(true);
      }
    });

    myListModel = new DefaultListModel();
    myList = new JBList(myListModel);
    myList.setFocusable(false);
    myList.setSelectionMode(allowMultipleSelection ? ListSelectionModel.MULTIPLE_INTERVAL_SELECTION :
                            ListSelectionModel.SINGLE_SELECTION);
    myList.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        if (!myTextField.hasFocus()) {
          myTextField.requestFocus();
        }

        if (e.getClickCount() == 2) {
          if (myList.getSelectedValue() == EXTRA_ELEM) {
            myMaximumListSizeLimit += MAXIMUM_LIST_SIZE_LIMIT;
            rebuildList(myList.getSelectedIndex(), REBUILD_DELAY, null, ModalityState.current(), e);
            e.consume();
          }
          else {
            doClose(true);
          }
        }
      }
    });
    myList.setCellRenderer(myModel.getListCellRenderer());
    myList.setFont(editorFont);

    myList.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        choosenElementMightChange();
        updateDocumentation();
      }
    });

    myListScrollPane = ScrollPaneFactory.createScrollPane(myList);
    myListScrollPane.setViewportBorder(new EmptyBorder(0, 0, 0, 0));

    myTextFieldPanel.setBorder(new EmptyBorder(2, 2, 2, 2));

    showTextFieldPanel();

    if (modalityState != null) {
      rebuildList(myInitialIndex, 0, null, modalityState, null);
    }
  }

  private void addCard(JComponent comp, String cardId) {
    JPanel wrapper = new JPanel(new BorderLayout());
    wrapper.add(comp, BorderLayout.EAST);
    myCardContainer.add(wrapper, cardId);
  }

  private static Set<KeyStroke> getShortcuts(@NotNull String actionId) {
    Set<KeyStroke> result = new HashSet<KeyStroke>();
    Keymap keymap = KeymapManager.getInstance().getActiveKeymap();
    Shortcut[] shortcuts = keymap.getShortcuts(actionId);
    if (shortcuts == null) {
      return result;
    }
    for (Shortcut shortcut : shortcuts) {
      if (shortcut instanceof KeyboardShortcut) {
        KeyboardShortcut keyboardShortcut = (KeyboardShortcut)shortcut;
        result.add(keyboardShortcut.getFirstKeyStroke());
      }
    }
    return result;
  }

  private void hideHint() {
    if (!myTextFieldPanel.focusRequested()) {
      doClose(false);
      myTextFieldPanel.hideHint();
    }
  }

  /**
   * Default rebuild list. It uses {@link #REBUILD_DELAY} and current modality state.
   */
  public void rebuildList(boolean initial) {
    // TODO this method is public, because the chooser does not listed for the model.
    rebuildList(initial ? myInitialIndex : 0, REBUILD_DELAY, null, ModalityState.current(), null);
  }

  private void updateDocPosition() {
    final JBPopup hint = myTextFieldPanel.getHint();
    if (hint != null) {
      SwingUtilities.invokeLater(new Runnable() {

        public void run() {
          if (myTextFieldPanel != null) myTextFieldPanel.repositionHint();
        }
      });
    }
  }

  private void updateDocumentation() {
    final JBPopup hint = myTextFieldPanel.getHint();
    final Object element = getChosenElement();
    if (hint != null) {
      if (element instanceof PsiElement) {
        myTextFieldPanel.updateHint((PsiElement)element);
      }
      else if (element instanceof DataProvider) {
        final Object o = ((DataProvider)element).getData(LangDataKeys.PSI_ELEMENT.getName());
        if (o instanceof PsiElement) {
          myTextFieldPanel.updateHint((PsiElement)o);
        }
      }
    }
  }

  public String transformPattern(String pattern) {
    return pattern;
  }

  protected void doClose(final boolean ok) {
    if (checkDisposed()) return;

    if (postponeCloseWhenListReady(ok)) return;

    cancelListUpdater();
    close(ok);

    //clearPosponedOkAction(ok);
  }

  protected void cancelListUpdater() {
    myListUpdater.cancelAll();
  }

  private boolean postponeCloseWhenListReady(boolean ok) {
    if (!isToFixLostTyping()) return false;

    final String text = myTextField.getText();
    if (ok && !myListIsUpToDate && text != null && text.trim().length() > 0) {
      myPosponedOkAction = new ActionCallback();
     //IdeFocusManager.getInstance(myProject).typeAheadUntil(myPosponedOkAction);
      return true;
    }

    return false;
  }

  protected static boolean isToFixLostTyping() {
    return Registry.is("actionSystem.fixLostTyping");
  }

  private synchronized void ensureNamesLoaded(boolean checkboxState) {
    int index = checkboxState ? 1 : 0;
    if (myNames[index] != null) return;

    Window window = (Window)SwingUtilities.getAncestorOfClass(Window.class, myTextField);
    //LOG.assertTrue (myTextField != null);
    //LOG.assertTrue (window != null);
    Window ownerWindow = null;
    if (window != null) {
      window.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
      ownerWindow = window.getOwner();
      if (ownerWindow != null) {
        ownerWindow.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
      }
    }
    myNames[index] = myModel.getNames(checkboxState);

    if (window != null) {
      window.setCursor(Cursor.getDefaultCursor());
      if (ownerWindow != null) {
        ownerWindow.setCursor(Cursor.getDefaultCursor());
      }
    }
  }

  public String[] getNames(boolean checkboxState) {
    return checkboxState ? myNames[1] : myNames[0];
  }

  protected abstract boolean isCheckboxVisible();

  protected abstract boolean isShowListForEmptyPattern();

  protected abstract boolean isCloseByFocusLost();

  protected void showTextFieldPanel() {
    final JLayeredPane layeredPane = getLayeredPane();
    final Dimension preferredTextFieldPanelSize = myTextFieldPanel.getPreferredSize();
    final int x = (layeredPane.getWidth() - preferredTextFieldPanelSize.width) / 2;
    final int paneHeight = layeredPane.getHeight();
    final int y = paneHeight / 3 - preferredTextFieldPanelSize.height / 2;

    VISIBLE_LIST_SIZE_LIMIT = Math.max
      (10, (paneHeight - (y + preferredTextFieldPanelSize.height)) / (preferredTextFieldPanelSize.height / 2) - 1);

    ComponentPopupBuilder builder = JBPopupFactory.getInstance().createComponentPopupBuilder(myTextFieldPanel, myTextField);
    builder.setCancelCallback(new Computable<Boolean>() {
      //@Override
      public Boolean compute() {
        myTextPopup = null;
        close(false);
        return Boolean.TRUE;
      }
    }).setFocusable(true).setRequestFocus(true)./*setForceHeavyweight(true).*/setModalContext(false).setCancelOnClickOutside(false);

    Point point = new Point(x, y);
    SwingUtilities.convertPointToScreen(point, layeredPane);
    Rectangle bounds = new Rectangle(point, new Dimension(preferredTextFieldPanelSize.width + 20 + 150, preferredTextFieldPanelSize.height));
    myTextPopup = builder.createPopup();
    myTextPopup.setSize(bounds.getSize());
    myTextPopup.setLocation(bounds.getLocation());

    new MnemonicHelper().register(myTextFieldPanel);
    final boolean previousUpdate;
    final DaemonCodeAnalyzer daemonCodeAnalyzer = DaemonCodeAnalyzer.getInstance(myProject);
    if (daemonCodeAnalyzer != null) {
      previousUpdate = /*((DaemonCodeAnalyzerImpl)daemonCodeAnalyzer).isUpdateByTimerEnabled();*/false;
      daemonCodeAnalyzer.setUpdateByTimerEnabled(false);
    }
    else {
      previousUpdate = false;
    }

    Disposer.register(myTextPopup, new Disposable() {
      //@Override
      public void dispose() {
        if (daemonCodeAnalyzer != null) {
          daemonCodeAnalyzer.setUpdateByTimerEnabled(previousUpdate);
        }
      }
    });
    myTextPopup.show(layeredPane);
  }

  private JLayeredPane getLayeredPane() {
    JLayeredPane layeredPane;
    final Window window = WindowManager.getInstance().suggestParentWindow(myProject);

    Component parent = UIUtil.findUltimateParent(window);

    if (parent instanceof JFrame) {
      layeredPane = ((JFrame)parent).getLayeredPane();
    }
    else if (parent instanceof JDialog) {
      layeredPane = ((JDialog)parent).getLayeredPane();
    }
    else {
      throw new IllegalStateException("cannot find parent window: project=" + myProject +
                                      (myProject != null ? "; open=" + myProject.isOpen() : "") +
                                      "; window=" + window);
    }
    return layeredPane;
  }

  private final Object myRebuildMutex = new Object();

    protected void rebuildList(final int pos, final int delay, @Nullable final Runnable postRunnable,
                             final ModalityState modalityState,
                             @Nullable final ComponentEvent e) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myListIsUpToDate = false;
    myAlarm.cancelAllRequests();
    myListUpdater.cancelAll();

    cancelCalcElementsThread();
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        final String text = myTextField.getText();
        if (!isShowListForEmptyPattern()/*canShowListForEmptyPattern()*/ &&
            (text == null || text.trim().length() == 0)) {
          myListModel.clear();
          hideList();
          if (myTextFieldPanel != null) myTextFieldPanel.hideHint();
          myCard.show(myCardContainer, CHECK_BOX_CARD);
          return;
        }
        final Runnable request = new Runnable() {
          public void run() {
            final CalcElementsCallback callback = new CalcElementsCallback() {
              public void run(final Set<?> elements) {
                synchronized (myRebuildMutex) {
                  ApplicationManager.getApplication().assertIsDispatchThread();
                  if (checkDisposed()) {
                    return;
                  }

                  myListIsUpToDate = true;
                  setElementsToList(pos, elements);
                  myList.repaint();
                  choosenElementMightChange();

                  if (elements.isEmpty() && myTextFieldPanel != null) {
                    myTextFieldPanel.hideHint();
                  }

                  if (postRunnable != null) {
                    postRunnable.run();
                  }
                }
              }
            };

            cancelCalcElementsThread();

            final ListCellRenderer cellRenderer = myList.getCellRenderer();
            /*if (cellRenderer instanceof MatcherHolder) {
              final String pattern = transformPattern(text);
              final Matcher matcher = buildPatternMatcher(isSearchInAnyPlace() ? "*" + pattern + "*" : pattern);
              ((MatcherHolder)cellRenderer).setPatternMatcher(matcher);
            }  */

            myCalcElementsThread = new CalcElementsThread(text, myCheckBox.isSelected(), callback, modalityState, postRunnable == null);
            ApplicationManager.getApplication().executeOnPooledThread(myCalcElementsThread);
          }
        };

        if (delay > 0) {
          myAlarm.addRequest(request, delay, ModalityState.stateForComponent(myTextField));
        }
        else {
          request.run();
        }
      }
    }, modalityState);
  }

  protected boolean isToBuildListOnPolledThread() {
    return true;
  }

  private boolean isShowListAfterCompletionKeyStroke() {
    return myShowListAfterCompletionKeyStroke;
  }

  private void cancelCalcElementsThread() {
    if (myCalcElementsThread != null) {
      myCalcElementsThread.cancel();
      myCalcElementsThread = null;
    }
  }

  private void setElementsToList(int pos, Set<?> elements) {
    myListUpdater.cancelAll();
    if (checkDisposed()) return;
    if (elements.isEmpty()) {
      myListModel.clear();
      myTextField.setForeground(Color.red);
      myListUpdater.cancelAll();
      hideList();
     // clearPosponedOkAction(false);
      return;
    }

    Object[] oldElements = myListModel.toArray();
    Object[] newElements = elements.toArray();
    Diff.Change change = null;
    try {
      change = Diff.buildChanges(oldElements, newElements);
    }
    catch (Exception e) {
      // should not occur
    }

    if (change == null) {
      myListUpdater.doPostponedOkIfNeeded();
      return; // Nothing changed
    }

    List<Cmd> commands = new ArrayList<Cmd>();
    int inserted = 0;
    int deleted = 0;
    while (change != null) {
      if (change.deleted > 0) {
        final int start = change.line0 + inserted - deleted;
        commands.add(new RemoveCmd(start, start + change.deleted - 1));
      }

      if (change.inserted > 0) {
        for (int i = 0; i < change.inserted; i++) {
          commands.add(new InsertCmd(change.line0 + i + inserted - deleted, newElements[change.line1 + i]));
        }
      }

      deleted += change.deleted;
      inserted += change.inserted;
      change = change.link;
    }

    myTextField.setForeground(UIUtil.getTextFieldForeground());
    if (!commands.isEmpty()) {
      showList();
      myListUpdater.appendToModel(commands, pos);
    }
    else {
      if (pos <= 0) {
       // pos = detectBestStatisticalPosition();
      }

      ListScrollingUtil.selectItem(myList, Math.min(pos, myListModel.size() - 1));
      myList.setVisibleRowCount(Math.min(VISIBLE_LIST_SIZE_LIMIT, myList.getModel().getSize()));
      showList();
      updateDocPosition();
    }
  }

  private int detectBestStatisticalPosition() {
    int best = 0;
    int bestPosition = 0;
    /*int bestMatch = Integer.MIN_VALUE;
    final int count = myListModel.getSize();

    Matcher matcher = buildPatternMatcher(transformPattern(myTextField.getText()));

    final String statContext = statisticsContext();
    for (int i = 0; i < count; i++) {
      final Object modelElement = myListModel.getElementAt(i);
      String text = EXTRA_ELEM.equals(modelElement) ? null : myModel.getFullName(modelElement);
      if (text != null) {
        String shortName = myModel.getElementName(modelElement);
        int match = shortName != null && matcher instanceof NameUtil.MinusculeMatcher ? ((NameUtil.MinusculeMatcher)matcher).matchingDegree(shortName) : Integer.MIN_VALUE;
        int stats = StatisticsManager.getInstance().getUseCount(new StatisticsInfo(statContext, text));
        if (stats > best || stats == best && match > bestMatch) {
          best = stats;
          bestPosition = i;
          bestMatch = match;
        }
      }
    }  */

    return bestPosition;
  }

  @NonNls
  protected String statisticsContext() {
    return "choose_by_name#" + myModel.getPromptText() + "#" + myCheckBox.isSelected() + "#" + myTextField.getText();
  }

  private interface Cmd {
    void apply();
  }

  private class RemoveCmd implements Cmd {
    private final int start;
    private final int end;

    private RemoveCmd(final int start, final int end) {
      this.start = start;
      this.end = end;
    }

    public void apply() {
      myListModel.removeRange(start, end);
    }
  }

  private class InsertCmd implements Cmd {
    private final int idx;
    private final Object element;

    private InsertCmd(final int idx, final Object element) {
      this.idx = idx;
      this.element = element;
    }

    public void apply() {
      if (idx < myListModel.size()) {
        myListModel.add(idx, element);
      }
      else {
        myListModel.addElement(element);
      }
    }
  }

  private class ListUpdater {
    private final Alarm myAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
    private static final int DELAY = 10;
    private static final int MAX_BLOCKING_TIME = 30;
    private final List<Cmd> myCommands = Collections.synchronizedList(new ArrayList<Cmd>());

    public void cancelAll() {
      myCommands.clear();
      myAlarm.cancelAllRequests();
    }

    public void appendToModel(final List<Cmd> commands, final int selectionPos) {
      myAlarm.cancelAllRequests();
      myCommands.addAll(commands);

      if (myCommands.isEmpty() || checkDisposed()) {
        return;
      }
      myAlarm.addRequest(new Runnable() {
        public void run() {
          if (checkDisposed()) {
            return;
          }
          final long startTime = System.currentTimeMillis();
          while (!myCommands.isEmpty() && System.currentTimeMillis() - startTime < MAX_BLOCKING_TIME) {
            final Cmd cmd = myCommands.remove(0);
            cmd.apply();
          }

          myList.setVisibleRowCount(Math.min(VISIBLE_LIST_SIZE_LIMIT, myList.getModel().getSize()));
          if (!myListModel.isEmpty()) {
            int pos = selectionPos <= 0 ? detectBestStatisticalPosition() : selectionPos;
            ListScrollingUtil.selectItem(myList, Math.min(pos, myListModel.size() - 1));
          }

          if (!myCommands.isEmpty()) {
            myAlarm.addRequest(this, DELAY);
          }
          else {
            doPostponedOkIfNeeded();
          }
          if (!checkDisposed()) {
            showList();
            updateDocPosition();
          }
        }
      }, DELAY);
    }

    private void doPostponedOkIfNeeded() {
      if (myPosponedOkAction != null) {
        if (getChosenElement() != null) {
          doClose(true);
        }
        //clearPosponedOkAction(checkDisposed());
      }
    }
  }

  protected abstract void showList();

  protected abstract void hideList();

  protected abstract void close(boolean isOk);

  @Nullable
  public Object getChosenElement() {
    final List<Object> elements = getChosenElements();
    return elements != null && elements.size() == 1 ? elements.get(0) : null;
  }

  protected List<Object> getChosenElements() {
      return  new ArrayList<Object>(Arrays.asList(myList.getSelectedValues()));
            //  Collections.emptyList();
    /*if (myListIsUpToDate) {
      List<Object> values = new ArrayList<Object>(Arrays.asList(myList.getSelectedValues()));
      values.remove(EXTRA_ELEM);
      return values;
    }

    final String text = myTextField.getText();
    final boolean checkBoxState = myCheckBox.isSelected();
    //ensureNamesLoaded(checkBoxState);
    final String[] names = checkBoxState ? myNames[1] : myNames[0];
    if (names == null) return Collections.emptyList();

    Object uniqueElement = null;

    for (final String name : names) {
      if (text.equalsIgnoreCase(name)) {
        final Object[] elements = myModel.getElementsByName(name, checkBoxState, text);
        if (elements.length > 1) return Collections.emptyList();
        if (elements.length == 0) continue;
        if (uniqueElement != null) return Collections.emptyList();
        uniqueElement = elements[0];
      }
    }
    return uniqueElement == null ? Collections.emptyList() : Collections.singletonList(uniqueElement); */
  }

  protected void choosenElementMightChange() {
  }

  protected final class MyTextField extends JTextField implements PopupOwner, TypeSafeDataProvider {
    private final KeyStroke myCompletionKeyStroke;
    private final KeyStroke forwardStroke;
    private final KeyStroke backStroke;

    private boolean completionKeyStrokeHappened = false;

    private MyTextField() {
      super(40);
      enableEvents(AWTEvent.KEY_EVENT_MASK);
      myCompletionKeyStroke = getShortcut(IdeActions.ACTION_CODE_COMPLETION);
      forwardStroke = getShortcut(IdeActions.ACTION_GOTO_FORWARD);
      backStroke = getShortcut(IdeActions.ACTION_GOTO_BACK);
      setFocusTraversalKeysEnabled(false);
    }

    private KeyStroke getShortcut(String actionCodeCompletion) {
      final Shortcut[] shortcuts = KeymapManager.getInstance().getActiveKeymap().getShortcuts(actionCodeCompletion);
      for (final Shortcut shortcut : shortcuts) {
        if (shortcut instanceof KeyboardShortcut) {
          return ((KeyboardShortcut)shortcut).getFirstKeyStroke();
        }
      }
      return null;
    }

    //@Override
    public void calcData(final DataKey key, final DataSink sink) {
        if (myTextPopup != null && myTextPopup.isVisible()) {
          //sink.put(key, myTextPopup);
        }
    }

    protected void processKeyEvent(KeyEvent e) {
      final KeyStroke keyStroke = KeyStroke.getKeyStrokeForEvent(e);

      if (myCompletionKeyStroke != null && keyStroke.equals(myCompletionKeyStroke)) {
        completionKeyStrokeHappened = true;
        e.consume();
        final String pattern = myTextField.getText();
        final String oldText = myTextField.getText();
        final int oldPos = myList.getSelectedIndex();
        myHistory.add(Pair.create(oldText, oldPos));
        final Runnable postRunnable = new Runnable() {
          public void run() {
            fillInCommonPrefix(pattern);
          }
        };
        rebuildList(0, 0, postRunnable, ModalityState.current(), e);
        return;
      }
      if (backStroke != null && keyStroke.equals(backStroke)) {
        e.consume();
        if (!myHistory.isEmpty()) {
          final String oldText = myTextField.getText();
          final int oldPos = myList.getSelectedIndex();
          final Pair<String, Integer> last = myHistory.remove(myHistory.size() - 1);
          myTextField.setText(last.first);
          myFuture.add(Pair.create(oldText, oldPos));
          rebuildList(0, 0, null, ModalityState.current(), e);
        }
        return;
      }
      if (forwardStroke != null && keyStroke.equals(forwardStroke)) {
        e.consume();
        if (!myFuture.isEmpty()) {
          final String oldText = myTextField.getText();
          final int oldPos = myList.getSelectedIndex();
          final Pair<String, Integer> next = myFuture.remove(myFuture.size() - 1);
          myTextField.setText(next.first);
          myHistory.add(Pair.create(oldText, oldPos));
          rebuildList(0, 0, null, ModalityState.current(), e);
        }
        return;
      }
      try {
        super.processKeyEvent(e);
      }
      catch (NullPointerException e1) {
        /*if (!Patches.SUN_BUG_6322854) {
          throw e1;
        } */
      }
    }

    private void fillInCommonPrefix(final String pattern) {
      final List<String> list =   new ArrayList<String>( Arrays.asList( getNames( myCheckBox.isSelected() ) ) ) ;
              //new ArrayList<String>();
              //myProvider.filterNames(ChooseByNameBase.this, getNames(myCheckBox.isSelected()), pattern);

      if (isComplexPattern(pattern)) return; //TODO: support '*'
      final String oldText = myTextField.getText();
      final int oldPos = myList.getSelectedIndex();

      String commonPrefix = null;
      if (!list.isEmpty()) {
        for (String name : list) {
          final String string = name.toLowerCase();
          if (commonPrefix == null) {
            commonPrefix = string;
          }
          else {
            while (commonPrefix.length() > 0) {
              if (string.startsWith(commonPrefix)) {
                break;
              }
              commonPrefix = commonPrefix.substring(0, commonPrefix.length() - 1);
            }
            if (commonPrefix.length() == 0) break;
          }
        }
        commonPrefix = list.get(0).substring(0, commonPrefix.length());
        for (int i = 1; i < list.size(); i++) {
          final String string = list.get(i).substring(0, commonPrefix.length());
          if (!string.equals(commonPrefix)) {
            commonPrefix = commonPrefix.toLowerCase();
            break;
          }
        }
      }
      if (commonPrefix == null) commonPrefix = "";
      final String newPattern = commonPrefix;

      myHistory.add(Pair.create(oldText, oldPos));
      myTextField.setText(newPattern);
      myTextField.setCaretPosition(newPattern.length());

      rebuildList(false);
    }

    private boolean isComplexPattern(final String pattern) {
      if (pattern.indexOf('*') >= 0) return true;
      for (String s : myModel.getSeparators()) {
        if (pattern.contains(s)) return true;
      }

      return false;
    }

    @Nullable
    public Point getBestPopupPosition() {
      return new Point(myTextFieldPanel.getWidth(), getHeight());
    }

    protected void paintComponent(final Graphics g) {
      UISettings.setupAntialiasing(g);
      super.paintComponent(g);
    }

    public boolean isCompletionKeyStroke() {
      return completionKeyStrokeHappened;
    }
  }
        private interface CalcElementsCallback {
    void run(Set<?> elements);
  }
  private static final String EXTRA_ELEM = "...";

  private class CalcElementsThread implements Runnable {
    private final String myPattern;
    private boolean myCheckboxState;
    private final CalcElementsCallback myCallback;
    private final ModalityState myModalityState;

    private Set<Object> myElements = null;

    private volatile boolean myCancelled = false;
    private final boolean myCanCancel;

    private CalcElementsThread( String pattern, boolean checkboxState,
                               CalcElementsCallback callback, ModalityState modalityState,
                               boolean canCancel ) {
      myPattern       = pattern;
      myCheckboxState = checkboxState;
      myCallback      = callback;
      myModalityState = modalityState;
      myCanCancel     = canCancel;
    }

    private final Alarm myShowCardAlarm = new Alarm();

    public void run() {
        showCard(SEARCHING_CARD, 200);

      final Set<Object> elements = new LinkedHashSet<Object>();
      Runnable action = new Runnable() {
        public void run() {
          try {
            ensureNamesLoaded(myCheckboxState);
            Computable<Boolean> cancelled = new Computable<Boolean>() {
              public Boolean compute() {
                return myCancelled;
              }
            };

            addElementsByPattern(myPattern, elements, cancelled);

            for (Object elem : elements) {
              if (myCancelled) {
                break;
              }
              if (elem instanceof PsiElement) {
                final PsiElement psiElement = (PsiElement)elem;
                psiElement.isWritable(); // That will cache writable flag in VirtualFile. Taking the action here makes it canceleable.
              }
            }
          }
          catch (ProcessCanceledException e) {
            //OK
          }
        }
      };
      ApplicationManager.getApplication().runReadAction(action);

      if (myCancelled) {
        myShowCardAlarm.cancelAllRequests();
        return;
      }

      final String cardToShow;
      if (elements.isEmpty() && !myCheckboxState) {
        myCheckboxState = true;
        ApplicationManager.getApplication().runReadAction(action);
        cardToShow = elements.isEmpty() ? NOT_FOUND_CARD : NOT_FOUND_IN_PROJECT_CARD;
      }
      else {
        cardToShow = elements.isEmpty() ? NOT_FOUND_CARD : CHECK_BOX_CARD;
      }
      showCard(cardToShow, 0);

      myElements = elements;

      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          myCallback.run(myElements);
        }
      }, myModalityState);
    }

    private void addElementsByPattern( String pattern, final Set<Object> elements,
                                      final Computable<Boolean> cancelled ) {
        //elements.clear();
        Object[] foundElements = myModel.getElementsByPattern( pattern );
        for ( int i = 0; i< foundElements.length; i++) {
            elements.add( foundElements[i] );
        }
    }

    private void showCard(final String card, final int delay) {
      myShowCardAlarm.cancelAllRequests();
      myShowCardAlarm.addRequest(new Runnable() {
        public void run() {
          myCard.show(myCardContainer, card);
        }
      }, delay, myModalityState);
    }

    protected boolean isOverflow(Set<Object> elementsArray) {
        return elementsArray.size() >= myMaximumListSizeLimit;
    }

    private void cancel() {
      if (myCanCancel) {
        myCancelled = true;
      }
    }
  }

  private static class HintLabel extends JLabel {
    private HintLabel(String text) {
      super(text, RIGHT);
      setForeground(Color.darkGray);
    }
  }

  private static final String ACTION_NAME = "Show All in View";
  private static final Icon FIND_ICON = IconLoader.getIcon("/actions/find.png");
}
