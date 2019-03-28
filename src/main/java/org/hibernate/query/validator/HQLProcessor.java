package org.hibernate.query.validator;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Set;

import static org.hibernate.query.validator.HQLProcessor.CHECK_HQL;

@SupportedAnnotationTypes({CHECK_HQL, "javax.persistence.*"})
//@AutoService(Processor.class)
public class HQLProcessor extends AbstractProcessor {

    static final String CHECK_HQL = "org.hibernate.query.validator.CheckHQL";

    private AbstractProcessor delegate;

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        String compiler = processingEnv.getClass().getName();
        if (compiler.endsWith("BatchProcessingEnvImpl")) {
            delegate = new ECJProcessor();
        } else if (compiler.endsWith("JavacProcessingEnvironment")) {
            delegate = new JavacProcessor();
        } else if (compiler.endsWith("IdeBuildProcessingEnvImpl")) {
            try {
                delegate = (AbstractProcessor)
                        Class.forName("org.hibernate.query.validator.EclipseProcessor")
                                .newInstance();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (delegate!=null) {
            delegate.init(processingEnv);
//            processingEnv.getMessager()
//                    .printMessage(Diagnostic.Kind.NOTE,
//                            "Installed Hibernate Query Validator");
        }
//        else {
//            processingEnv.getMessager()
//                    .printMessage(Diagnostic.Kind.NOTE,
//                            "Could not install Hibernate Query Validator");
//        }
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (delegate==null) {
            return false;
        }
        try {
//            processingEnv.getMessager()
//                    .printMessage(Diagnostic.Kind.MANDATORY_WARNING,
//                            "CALLED " + roundEnv.getRootElements().size());
            delegate.process(annotations, roundEnv);
        }
        catch (Throwable e) {
            processingEnv.getMessager()
                    .printMessage(Diagnostic.Kind.MANDATORY_WARNING, e.getMessage());
            StringWriter writer = new StringWriter();
            e.printStackTrace(new PrintWriter(writer));
            processingEnv.getMessager()
                    .printMessage(Diagnostic.Kind.MANDATORY_WARNING, writer.toString());
        }
        return false;
    }
}
