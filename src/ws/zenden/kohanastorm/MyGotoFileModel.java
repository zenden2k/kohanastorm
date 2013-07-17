package ws.zenden.kohanastorm; /**
 * Created by IntelliJ IDEA.
 * User: zenden
 * Date: 01.12.11
 * Time: 11:36
 * To change this template use File | Settings | File Templates.
 */


import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.ide.util.gotoByName.GotoFileCellRenderer;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiElement;

import java.util.ArrayList;
import java.util.List;
import java.net.*;
import java.io.*;

public class MyGotoFileModel implements MyModel {
  private final int myMaxSize;


  List<String> cachedFileList = new ArrayList<String> ();
  String cachedPattern = null;
  Project myProject  = null;
    VirtualFile webDir;


  public MyGotoFileModel(Project project,  VirtualFile webDir ) {
      myProject = project;
            this.webDir = webDir;


        myMaxSize = WindowManagerEx.getInstanceEx().getFrame(project).getSize().width;
  }

  protected boolean acceptItem(final NavigationItem item) {
    return true;
  }

  @Nullable
  //@Override
  protected FileType filterValueFor(NavigationItem item) {
    return item instanceof PsiFile ? ((PsiFile) item).getFileType() : null;
  }

  public String getPromptText() {
    return "Enter page full URL:";
  }

  public String getCheckBoxName() {
    return IdeBundle.message("checkbox.include.non.project.files");
  }

  public char getCheckBoxMnemonic() {
    return SystemInfo.isMac?'P':'n';
  }

  public String getNotInMessage() {
    return IdeBundle.message("label.no.non.java.files.found");
  }

  public String getNotFoundMessage() {
    return IdeBundle.message("label.no.files.found");
  }

  public boolean loadInitialCheckBoxState() {
    PropertiesComponent propertiesComponent = PropertiesComponent.getInstance(myProject);
    return propertiesComponent.isTrueValue("GoToClass.includeJavaFiles");
  }

  public void saveInitialCheckBoxState(boolean state) {
    PropertiesComponent propertiesComponent = PropertiesComponent.getInstance(myProject);
    propertiesComponent.setValue("GoToClass.includeJavaFiles", Boolean.toString(state));
  }

  public PsiElementListCellRenderer getListCellRenderer() {
      //return new DefaultPsiElementCellRenderer();
    return new GotoFileCellRenderer(myMaxSize);
  }

  @Nullable
  public String getFullName(final Object element) {
    if (element instanceof PsiFile) {
      final VirtualFile virtualFile = ((PsiFile)element).getVirtualFile();
      return virtualFile != null ? virtualFile.getPath() : null;
    }

    return getElementName(element);
  }

  @NotNull
  public String[] getSeparators() {
    return new String[] {"/", "\\"};
  }

    public String getHelpId() {
        return "procedures.navigating.goto.class";
    }

    public boolean willOpenEditor() {
        return true;
    }

    public Object[] getElementsByName(String name, boolean checkBoxState, String pattern) {
        return new Object[0];  //To change body of implemented methods use File | Settings | File Templates.
    }

    public String getElementName(Object element) {
        return "test";
    }

    public String[] getNames(boolean checkBoxState) {
        return new String[]{ "test", "test" };
    }

    public Object[] getElementsByPattern(String userPattern) {
        ArrayList<MyListElement> elements = new ArrayList<MyListElement>();
        MyListElement error= new MyListElement("", null, "error", "");

        try {
            PropertiesComponent properties = PropertiesComponent.getInstance(myProject);

            String secretKey = properties.getValue("kohanaStormSecretKey", DefaultSettings.secretKey);

            if ( userPattern.contains("?") ) {
                userPattern += "&";
            } else {
                userPattern += "?";
            }
            userPattern+=   "ks_secret_key=" + secretKey;

            URL url = new URL(userPattern);
            InputStream is = url.openStream();
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(
                            is));
            String inputLine;

            if ((inputLine = in.readLine()) != null)     {
                String[]  results = inputLine.split(";");
                if ( results.length < 5 ) {
                    error.setTitle("Server returned invalid result");
                    return new Object[]{error};
                }
                String head = results[0];
                String version = results[1];
                String directory = results[2];
                String controller = results[3];
                String action = results[4];
                if ( !directory.isEmpty() ) {
                    directory += "/";
                }
                controller = controller.replace('_','/');
                String controllersDir = properties.getValue("kohanaAppPath", DefaultSettings.kohanaAppPath)
                        + "classes/controller/";
               String path =   controllersDir + directory + controller + ".php";
               VirtualFile f = webDir.findFileByRelativePath(path);
                if ( f == null) {
                    f = webDir.findFileByRelativePath("lib" + path);
                }
                if ( f!= null ) {
                PsiFile psiTemplate = PsiManager.getInstance(this.myProject).findFile(f);
                String text = psiTemplate.getText();
                int index = text.indexOf(" action_" +  action);
                if (index == -1) {

                }
                    PsiElement el =  psiTemplate.findElementAt(index + 1);

                    MyListElement mle = new MyListElement("",f,"controller",path);
                    mle.psiElement = el;
                    elements.add(mle  );
                }
            }
        }    catch ( Exception ex) {

        }
        finally {
            //is.close();
        }
        return elements.toArray();

    }
}