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

@SupportedAnnotationTypes("*")
public class HQLProcessor extends AbstractProcessor {

    static final String CHECK_HQL = "org.hibernate.query.validator.CheckHQL";

    static String jpa(String name) {
        //sneak it past shadow
        return new StringBuilder("javax.")
                .append("persistence.")
                .append(name)
                .toString();
    }

    public static boolean forceEclipseForTesting = false;

    private AbstractProcessor delegate;

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        String compiler = processingEnv.getClass().getName();
        if (compiler.endsWith("IdeBuildProcessingEnvImpl")
                || forceEclipseForTesting) {
            //create it using reflection to allow
            //us to compile everything else w/o
            //the Groovy compiler being present
            delegate = newEclipseProcessor();
        } else if (compiler.endsWith("BatchProcessingEnvImpl")) {
            delegate = new ECJProcessor();
        } else if (compiler.endsWith("JavacProcessingEnvironment")) {
            delegate = new JavacProcessor();
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

    private static AbstractProcessor newEclipseProcessor() {
        try {
            return (AbstractProcessor)
                    Class.forName("org.hibernate.query.validator.EclipseProcessor")
                            .newInstance();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
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
            String message = e.getMessage();
            if (message==null) message = e.getClass().getName();
            processingEnv.getMessager()
                    .printMessage(Diagnostic.Kind.MANDATORY_WARNING, message);
            StringWriter writer = new StringWriter();
            e.printStackTrace(new PrintWriter(writer));
            processingEnv.getMessager()
                    .printMessage(Diagnostic.Kind.MANDATORY_WARNING, writer.toString());
        }
        return false;
    }
}
