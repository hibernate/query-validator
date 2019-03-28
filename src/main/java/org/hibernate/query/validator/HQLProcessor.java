package org.hibernate.query.validator;

import com.google.auto.service.AutoService;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import java.util.Set;

@SupportedAnnotationTypes("org.hibernate.query.validator.CheckHQL")
@AutoService(Processor.class)
public class HQLProcessor extends AbstractProcessor {

    private AbstractProcessor delegate;

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return delegate.getSupportedSourceVersion();
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        String compiler = processingEnv.getClass().getName();
        if (compiler.endsWith("IdeProcessingEnvImpl")) {
            try {
                delegate = (AbstractProcessor)
                        Class.forName("org.hibernate.query.validator.EclipseProcessor")
                                .newInstance();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if (compiler.endsWith("BatchProcessingEnvImpl")) {
            delegate = new ECJProcessor();
        } else if (compiler.endsWith("JavacProcessingEnvironment")) {
            delegate = new JavacProcessor();
        }
        delegate.init(processingEnv);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        return delegate.process(annotations, roundEnv);
    }
}
