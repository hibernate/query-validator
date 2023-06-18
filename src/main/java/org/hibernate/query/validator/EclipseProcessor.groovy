//file:noinspection GroovyFallthrough
package org.hibernate.query.validator


import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.TypeElement
import javax.tools.Diagnostic

/**
 * Annotation processor that validates HQL and JPQL queries
 * for Eclipse.
 *
 * @see org.hibernate.annotations.processing.CheckHQL
 *
 * @author Gavin King
 */
//@SupportedAnnotationTypes(CHECK_HQL)
class EclipseProcessor extends AbstractProcessor {

    private ProcessingEnvironment processingEnv

    synchronized void init(ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv
        super.init(processingEnv)
        processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "Hibernate Query Validator for Eclipse");
    }

    static Mocker<EclipseSessionFactory> sessionFactory = Mocker.variadic(EclipseSessionFactory.class)

    @Override
    boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        def compiler = processingEnv.getCompiler()
        if (!roundEnv.getRootElements().isEmpty()) {
            for (unit in compiler.unitsToProcess) {
                compiler.parser.getMethodBodies(unit)
                new EclipseChecker(unit, compiler, processingEnv).checkHQL()
            }
        }
        return false
    }

//    private final static String ORG_HIBERNATE =
//            new StringBuilder("org.")
//                    .append("hibernate.")
//                    .toString()

//    private static String shadow(String name) {
//        return name.replace(ORG_HIBERNATE + "dialect",
//                ORG_HIBERNATE + "query.validator.hibernate.dialect")
//    }

    @Override
    SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported()
    }

}
