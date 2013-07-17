package ws.zenden.kohanastorm;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.diagnostic.Logger;

import java.lang.reflect.Method;

public class MyPsiReferenceProvider extends PsiReferenceProvider {

    public static final PsiReferenceProvider[] EMPTY_ARRAY = new PsiReferenceProvider[0];

    public MyPsiReferenceProvider() {
    }

    @NotNull
    @Override
    public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull final ProcessingContext context) {
        Project project = element.getProject();

        PropertiesComponent properties = PropertiesComponent.getInstance(project);

        String kohanaAppDir = properties.getValue("kohanaAppPath", "application/");

        VirtualFile appDir = project.getBaseDir().findFileByRelativePath(kohanaAppDir);

        if (appDir == null) {
            return PsiReference.EMPTY_ARRAY;
        }
        String className = element.getClass().getName();
        Class elementClass = element.getClass();
        if (className.endsWith("StringLiteralExpressionImpl")) {
            try {
                Method method = elementClass.getMethod("getValueRange");
                Object obj = method.invoke(element);
                TextRange textRange = (TextRange) obj;
                Class _PhpPsiElement = elementClass.getSuperclass().getSuperclass().getSuperclass();
                Method phpPsiElementGetText = _PhpPsiElement.getMethod("getText");
                Object obj2 = phpPsiElementGetText.invoke(element);
                String str = obj2.toString();
                String uri = str.substring(textRange.getStartOffset(), textRange.getEndOffset());
                int start = textRange.getStartOffset();
                int len = textRange.getLength();
                if (uri.endsWith(".tpl") || uri.startsWith("smarty:") || isViewFactoryCall(element)) {
                    PsiReference ref = new MyReference(uri, element, new TextRange(start, start + len), project, appDir);
                    return new PsiReference[]{ref};
                }

            } catch (Exception e) {
            }
        }

        return PsiReference.EMPTY_ARRAY;
    }

    public static boolean isViewFactoryCall(PsiElement element) {
        PsiElement prevEl = element.getParent();

        String elClassName;
        if (prevEl != null) {
            elClassName = prevEl.getClass().getName();
        }
        prevEl = prevEl.getParent();
        if (prevEl != null) {
            elClassName = prevEl.getClass().getName();
            if (elClassName.endsWith("MethodReferenceImpl")) {
                try {
                    //Class PhpReferenceImplClass = Class.forName("com.jetbrains.php.lang.psi.elements.impl.PhpReferenceImpl");
                    Method phpPsiElementGetName = prevEl.getClass().getMethod("getName");
                    String name = (String) phpPsiElementGetName.invoke(prevEl);
                    if (name.toLowerCase().equals("factory")) {
                        // Class MemberReferenceClass = prevEl.getClass().getSuperclass();
                        Method getClassReference = prevEl.getClass().getMethod("getClassReference");
                        Object classRef = getClassReference.invoke(prevEl);
                        PrintElementClassDescription(classRef);
                        String phpClassName = (String) phpPsiElementGetName.invoke(classRef);
                        if (phpClassName.toLowerCase().equals("view")) {
                            return true;
                        }

                    }
                } catch (Exception ex) {

                }
            }
        }
        return false;
    }

    public static String PrintElementClassDescription(Object element) {

        Logger log = Logger.getInstance("ERROR");
        String classDescription = "";
        Class parentclass = element.getClass();
        do {
            classDescription += "\r\nPSICLASS " + parentclass.toString() + "\r\n";

            Class[] intefaces = parentclass.getInterfaces();
            for (Class interfac : intefaces) {
                classDescription += "\r\nIMPLEMENTS INTERFACE " + interfac.getName() + "\r\n";
            }

            //Get the methods
            Method[] methods = parentclass.getDeclaredMethods();

            //Loop through the methods and print out their names
            for (Method method : methods) {
                String singature = method.getName() + "(";
                Class[] params = method.getParameterTypes();
                for (Class clas : params) {
                    singature += clas.getName() + ",";
                }
                singature += ")";
                classDescription += "\r\n PSICLASS method: " + singature;
            }
            parentclass = parentclass.getSuperclass();


        } while (parentclass != null);
        return classDescription;
    }


}
