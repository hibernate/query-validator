package org.hibernate.query.validator;

import com.google.auto.service.AutoService;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.tools.javac.code.Attribute;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeScanner;
import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.hql.internal.antlr.HqlSqlTokenTypes;
import org.hibernate.hql.internal.ast.*;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.util.Elements;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.util.Collections.emptyMap;

@SupportedAnnotationTypes("org.hibernate.query.validator.CheckHQL")
@AutoService(Processor.class)
public class HQLValidatingProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
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
                                    new JavacErrorReporter(jcLiteral, element, processingEnv);
                            try {

                                HqlParser parser = HqlParser.getInstance(hql);
                                setHandler(parser, handler);
                                parser.statement();

                                if (handler.getErrorCount()==0) {
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
                                    HqlSqlWalker walker = new HqlSqlWalker(
                                            new QueryTranslatorImpl("", hql, emptyMap(), factory),
                                            factory, parser, emptyMap(), null);
                                    walker.setASTFactory(new SqlASTFactory(walker) {
                                        @Override
                                        public Class getASTNodeType(int tokenType) {
                                            return tokenType == HqlSqlTokenTypes.CONSTRUCTOR ?
                                                    WorkaroundConstructorNode.class :
                                                    super.getASTNodeType(tokenType);
                                        }
                                    });
                                    setHandler(walker, handler);
                                    try {
                                        walker.statement(parser.getAST());
                                    }
                                    catch (HibernateException e) {
                                        String message = e.getMessage();
                                        if (message!=null) {
                                            handler.reportError(message);
                                        }
                                    }
                                    catch (Exception e) {
                                        //throw away NullPointerExceptions and the like
                                        //since I guess they represent bugs in Hibernate
//                                        e.printStackTrace();
                                    }

                                    //don't use this much simpler implementation
                                    //because it does too much stuff (generates SQL)
                                    //  queryTranslator.compile(null, false);
                                }

                            }
                            catch (Exception e) {
                                e.printStackTrace();
                            }
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

    private static void setHandler(Object object, ParseErrorHandler handler) {
        try {
            Field field = object.getClass().getDeclaredField("parseErrorHandler");
            field.setAccessible(true);
            field.set(object, handler);
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

}
