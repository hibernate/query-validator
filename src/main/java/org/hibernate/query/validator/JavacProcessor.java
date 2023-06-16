package org.hibernate.query.validator;

import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.util.Context;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.util.Set;


/**
 * Annotation processor that validates HQL and JPQL queries
 * for `javac`.
 *
 * @see CheckHQL
 *
 * @author Gavin King
 */
//@SupportedAnnotationTypes(CHECK_HQL)
public class JavacProcessor extends AbstractProcessor {

    static Mocker<JavacSessionFactory> sessionFactory = Mocker.variadic(JavacSessionFactory.class);

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        ModularityWorkaround.addOpens();
        super.init(processingEnv);
        processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "Hibernate Query Validator for Javac");
    }

    ProcessingEnvironment getProcessingEnv() {
        return processingEnv;
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        final JavacChecker javacChecker = new JavacChecker(this);
        for (Element element : roundEnv.getRootElements()) {
            if (element instanceof PackageElement) {
//                for (Element member : element.getEnclosedElements()) {
//                    checkHQL(member);
//                }
            }
            else {
                javacChecker.checkHQL(element);
            }
        }
        return false;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    Context getContext() {
        return ((JavacProcessingEnvironment) processingEnv).getContext();
    }
}
