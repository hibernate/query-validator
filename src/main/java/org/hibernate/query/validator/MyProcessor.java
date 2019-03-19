package org.hibernate.query.validator;

import com.google.auto.service.AutoService;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeScanner;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.Name;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import java.util.Set;

@SupportedAnnotationTypes("org.hibernate.query.validator.CheckHQL")
@AutoService(Processor.class)
public class MyProcessor extends AbstractProcessor {
    Messager messager;

    @Override
    public void init(ProcessingEnvironment env) {
        messager = env.getMessager();
        super.init(env);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (TypeElement annotation : annotations) {
            for (Element element : roundEnv.getElementsAnnotatedWith(annotation)) {
                if (element instanceof PackageElement) {
                    for (Element ee : element.getEnclosedElements()) {
                        checkHQL(ee);
                    }
                } else {
                    checkHQL(element);
                }
            }
        }
        return true;
    }

    private void checkHQL(Element element) {
        Elements elementUtils = processingEnv.getElementUtils();
        if (elementUtils instanceof JavacElements) {
            JCTree tree = ((JavacElements) elementUtils).getTree(element);
            if (tree != null) {
                tree.accept(new TreeScanner() {
                    @Override
                    public void visitApply(JCTree.JCMethodInvocation jcMethodInvocation) {
                        Name name = getMethodName(jcMethodInvocation.getMethodSelect());
                        if (name != null && name.toString().equals("createQuery")) {
                            super.visitApply(jcMethodInvocation);
                        }
                    }

                    @Override
                    public void visitLiteral(JCTree.JCLiteral jcLiteral) {
                        if (jcLiteral.value instanceof String) {
                            String string = (String) jcLiteral.value;
                            messager.printMessage(Diagnostic.Kind.WARNING, string);
                        }
                        super.visitLiteral(jcLiteral);
                    }
                });
            }
        }
    }

    private static Name getMethodName(ExpressionTree select) {
        if (select instanceof MemberSelectTree) {
            MemberSelectTree ref = (MemberSelectTree) select;
            return ref.getIdentifier();
        } else if (select instanceof IdentifierTree) {
            IdentifierTree ref = (IdentifierTree) select;
            return ref.getName();
        } else {
            return null;
        }
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }
}