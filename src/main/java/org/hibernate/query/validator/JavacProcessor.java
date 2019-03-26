package org.hibernate.query.validator;

import antlr.RecognitionException;
import com.google.auto.service.AutoService;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.tools.javac.code.Attribute;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Pair;
import org.hibernate.QueryException;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.hql.internal.ast.ParseErrorHandler;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.util.Elements;
import javax.tools.JavaFileObject;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.hibernate.query.validator.Validation.validate;

/**
 * Annotation processor that validates HQL and JPQL queries
 * for `javac`.
 *
 * @see CheckHQL
 */
@SupportedAnnotationTypes("org.hibernate.query.validator.CheckHQL")
@AutoService(Processor.class)
public class JavacProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (!(processingEnv instanceof JavacProcessingEnvironment)) {
            return false;
        }
        for (TypeElement annotation : annotations) {
            for (Element element : roundEnv.getElementsAnnotatedWith(annotation)) {
                if (element instanceof PackageElement) {
                    for (Element member : element.getEnclosedElements()) {
                        checkHQL(member);
                    }
                } else {
                    checkHQL(element);
                }
            }
        }
        return true;
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        if (processingEnv instanceof JavacProcessingEnvironment) {
            JavacHelper.initialize((JavacProcessingEnvironment) processingEnv);
        }
    }

    private void checkHQL(Element element) {
        Elements elementUtils = processingEnv.getElementUtils();
        if (elementUtils instanceof JavacElements) {
            JCTree tree = ((JavacElements) elementUtils).getTree(element);
            if (tree != null) {
                tree.accept(new TreeScanner() {
                    boolean inCreateQueryMethod = false;
                    boolean strict = true;

                    private void setStrictFromSuppressWarnings(
                            AnnotationMirror annotation) {
                        for (AnnotationValue value:
                                annotation.getElementValues().values()) {
                            @SuppressWarnings("unchecked")
                            List<Attribute> list = (List) value.getValue();
                            for (Attribute val: list) {
                                if (val.getValue().toString()
                                        .equals("hql.unknown-function")) {
                                    strict = false;
                                }
                            }
                        }
                    }

                    @Override
                    public void visitApply(JCTree.JCMethodInvocation jcMethodInvocation) {
                        String name = getMethodName(jcMethodInvocation.meth);
                        if ("createQuery".equals(name)) {
                            inCreateQueryMethod = true;
                            super.visitApply(jcMethodInvocation);
                            inCreateQueryMethod = false;
                        }
                        else {
                            super.visitApply(jcMethodInvocation); //needed!
                        }
                    }

                    @Override
                    public void visitAnnotation(JCTree.JCAnnotation jcAnnotation) {
                        AnnotationMirror annotation = jcAnnotation.attribute;
                        switch (annotation.getAnnotationType().toString()) {
                            case "java.lang.SuppressWarnings":
                                setStrictFromSuppressWarnings(annotation);
                                break;
                            case "javax.persistence.NamedQuery":
                                for (JCTree.JCExpression arg: jcAnnotation.args) {
                                    if (arg instanceof JCTree.JCAssign) {
                                        JCTree.JCAssign assign = (JCTree.JCAssign) arg;
                                        if ("query".equals(assign.lhs.toString())) {
                                            inCreateQueryMethod = true;
                                            super.visitAssign(assign);
                                            inCreateQueryMethod = false;
                                        }
                                    }
                                }
                                break;
                            default:
                                super.visitAnnotation(jcAnnotation); //needed!
                        }
                    }

                    @Override
                    public void visitClassDef(JCTree.JCClassDecl jcClassDecl) {
                        boolean s = strict;
                        super.visitClassDef(jcClassDecl);
                        strict = s;
                    }

                    @Override
                    public void visitMethodDef(JCTree.JCMethodDecl jcMethodDecl) {
                        boolean s = strict;
                        super.visitMethodDef(jcMethodDecl);
                        strict = s;
                    }

                    @Override
                    public void visitLiteral(JCTree.JCLiteral jcLiteral) {
                        Object literalValue = jcLiteral.value;
                        if (inCreateQueryMethod && literalValue instanceof String) {
                            String hql = (String) literalValue;

                            ParseErrorHandler handler =
                                    new JavacErrorReporter(jcLiteral, element);

                            SessionFactoryImplementor factory =
                                    new JavacSessionFactory() {
                                        private Set<String> unknowns = new HashSet<>();
                                        @Override
                                        void unknownSqlFunction(String functionName) {
                                            if (strict && unknowns.add(functionName)) {
                                                handler.reportWarning(functionName
                                                        + " is not defined");
                                            }
                                        }
                                    };

                            validate(hql, handler, factory);
                        }
                    }
                });
            }
        }
    }

    private static String getMethodName(ExpressionTree select) {
        if (select instanceof MemberSelectTree) {
            MemberSelectTree ref = (MemberSelectTree) select;
            return ref.getIdentifier().toString();
        } else if (select instanceof IdentifierTree) {
            IdentifierTree ref = (IdentifierTree) select;
            return ref.getName().toString();
        } else {
            return null;
        }
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    class JavacErrorReporter implements ParseErrorHandler {

        private Log log;
        private JCTree.JCLiteral jcLiteral;

        JavacErrorReporter(JCTree.JCLiteral jcLiteral, Element element) {
            this.jcLiteral = jcLiteral;

            Context context = ((JavacProcessingEnvironment) processingEnv).getContext();
            log = Log.instance(context);
            Pair pair = JavacElements.instance(context)
                    .getTreeAndTopLevel(element, null, null);
            JavaFileObject sourcefile = pair == null ? null :
                    ((JCTree.JCCompilationUnit) pair.snd).sourcefile;
            if (sourcefile != null) {
                log.useSource(sourcefile);
            }
        }

        @Override
        public int getErrorCount() {
            return 0;
        }

        @Override
        public void throwQueryException() throws QueryException {}

        @Override
        public void reportError(RecognitionException e) {
            log.error(jcLiteral.pos + e.column, "proc.messager",
                    e.getMessage());
        }

        @Override
        public void reportError(String text) {
            log.error(jcLiteral, "proc.messager", text);
        }

        @Override
        public void reportWarning(String text) {
            log.warning(jcLiteral, "proc.messager", text);
        }

    }
}
