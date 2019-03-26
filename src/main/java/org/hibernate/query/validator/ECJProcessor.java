package org.hibernate.query.validator;

import antlr.RecognitionException;
import com.google.auto.service.AutoService;
import org.eclipse.jdt.internal.compiler.ASTVisitor;
import org.eclipse.jdt.internal.compiler.Compiler;
import org.eclipse.jdt.internal.compiler.apt.dispatch.BaseProcessingEnvImpl;
import org.eclipse.jdt.internal.compiler.ast.*;
import org.eclipse.jdt.internal.compiler.lookup.BlockScope;
import org.hibernate.QueryException;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.hql.internal.ast.ParseErrorHandler;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.util.Set;

import static org.eclipse.jdt.core.compiler.CharOperation.charToString;
import static org.hibernate.query.validator.ECJHelper.qualifiedName;
import static org.hibernate.query.validator.Validation.validate;

/**
 * Annotation processor that validates HQL and JPQL queries
 * for ECJ.
 *
 * @see CheckHQL
 */
@SupportedAnnotationTypes("org.hibernate.query.validator.CheckHQL")
@AutoService(Processor.class)
public class ECJProcessor extends AbstractProcessor implements ParseErrorHandler {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (!(processingEnv instanceof BaseProcessingEnvImpl)) {
            return false;
        }
        for (TypeElement annotation : annotations) {
            for (Element element : roundEnv.getElementsAnnotatedWith(annotation)) {
                if (element instanceof PackageElement) {
                    for (Element root : roundEnv.getRootElements()) {
                        Element enclosing = root.getEnclosingElement();
                        if (enclosing!=null && enclosing.equals(element)) {
                            checkHQL(root);
                        }
                    }
                }
                else {
                    checkHQL(element);
                }
            }
        }
        return true;
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        if (processingEnv instanceof BaseProcessingEnvImpl) {
            ECJHelper.initialize((BaseProcessingEnvImpl) processingEnv);
        }

    }

    private void checkHQL(Element element) {
        if (processingEnv instanceof BaseProcessingEnvImpl) {
            Compiler compiler =
                    ((BaseProcessingEnvImpl) processingEnv)
                            .getCompiler();
            for (CompilationUnitDeclaration unit: compiler.unitsToProcess) {
                compiler.parser.getMethodBodies(unit);
                for (TypeDeclaration type: unit.types) {
                    if (qualifiedName(type.binding).equals(element.toString())) {
                        type.traverse(new ASTVisitor() {
                            boolean inCreateQueryMethod = false;

                            @Override
                            public boolean visit(MessageSend messageSend, BlockScope scope) {
                                inCreateQueryMethod =
                                        charToString(messageSend.selector)
                                                .equals("createQuery");
                                return true;
                            }

                            @Override
                            public void endVisit(MessageSend messageSend, BlockScope scope) {
                                inCreateQueryMethod = false;
                            }

                            @Override
                            public boolean visit(MemberValuePair pair, BlockScope scope) {
                                if (qualifiedName(pair.binding)
                                        .equals("javax.persistence.NamedQuery.query")) {
                                    inCreateQueryMethod = true;
                                }
                                return true;
                            }

                            @Override
                            public void endVisit(MemberValuePair pair, BlockScope scope) {
                                inCreateQueryMethod = false;
                            }

                            @Override
                            public void endVisit(StringLiteral stringLiteral, BlockScope scope) {
                                if (inCreateQueryMethod) {
                                    String hql = charToString(stringLiteral.source());

                                    SessionFactoryImplementor factory = new ECJSessionFactory();

                                    validate(hql, ECJProcessor.this, factory);
                                }

                            }
                        }, unit.scope);
                    }
                }
            }

        }
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public int getErrorCount() {
        return 0;
    }

    @Override
    public void throwQueryException() throws QueryException {}

    @Override
    public void reportError(RecognitionException e) {
        processingEnv.getMessager()
                .printMessage(Diagnostic.Kind.ERROR, e.getMessage());
    }

    @Override
    public void reportError(String s) {
        processingEnv.getMessager()
                .printMessage(Diagnostic.Kind.ERROR, s);
    }

    @Override
    public void reportWarning(String s) {
        processingEnv.getMessager()
                .printMessage(Diagnostic.Kind.WARNING, s);
    }

}
