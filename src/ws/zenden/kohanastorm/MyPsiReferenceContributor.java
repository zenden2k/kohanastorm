package ws.zenden.kohanastorm;

import com.intellij.patterns.StandardPatterns;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiElement;

public class MyPsiReferenceContributor extends PsiReferenceContributor {
    @Override
    public void registerReferenceProviders(PsiReferenceRegistrar registrar) {
        MyPsiReferenceProvider provider = new MyPsiReferenceProvider();

        //registrar.registerReferenceProvider(StandardPatterns.instanceOf(XmlAttributeValue.class), provider);
        //registrar.registerReferenceProvider(StandardPatterns.instanceOf(XmlTag.class), provider);

        registrar.registerReferenceProvider(StandardPatterns.instanceOf(PsiElement.class), provider);
    }
}
