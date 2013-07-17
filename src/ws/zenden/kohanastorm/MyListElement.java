package ws.zenden.kohanastorm;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.navigation.NavigationItem;
import com.intellij.psi.PsiElement;


class MyListElement {

    private String title;
    private VirtualFile file;
    private String descr;
    private String actionName;
    private boolean subElement = false;
    private NavigationItem navigationItem;
    public PsiElement psiElement;

    MyListElement(String title, VirtualFile file, String descr, String _actionName) {
        this.title = title;
        this.file = file;
        this.descr = descr;
        this.actionName = _actionName;
    }

    MyListElement(String title, VirtualFile file, String descr, String _actionName, boolean _subElement) {
        this.title = title;
        this.file = file;
        this.descr = descr;
        this.actionName = _actionName;
        this.subElement = _subElement;
    }

    String getTitle() {
        return title;
    }

    VirtualFile getFile() {
        return file;
    }

    void setTitle(String t) {
        actionName = t;
    }

    void setNavigationItem(NavigationItem item) {
        this.navigationItem = item;
    }

    NavigationItem getNavigationItem() {
        return navigationItem;
    }

    @Override
    public String toString() {
        String result = "[" + descr + "]  " /*file.getName()*/;
        if (actionName.length() != 0) {
            result += "  " + actionName;
        }
        if (subElement) {
            result = "   " + result;
        }
        return result;
    }

    boolean getSubElement() {
        return subElement;
    }
}