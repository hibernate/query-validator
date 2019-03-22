package org.hibernate.query.validator;

import com.google.auto.service.AutoService;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeScanner;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.hql.internal.antlr.HqlSqlTokenTypes;
import org.hibernate.hql.internal.ast.*;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.Name;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import java.lang.reflect.Field;
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

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        if (processingEnv instanceof JavacProcessingEnvironment) {
            JavacHelper.init((JavacProcessingEnvironment) processingEnv);
        }
    }

    private void checkHQL(Element element) {
        Elements elementUtils = processingEnv.getElementUtils();
        if (elementUtils instanceof JavacElements) {
            JCTree tree = ((JavacElements) elementUtils).getTree(element);
            if (tree != null) {
                tree.accept(new TreeScanner() {

                    boolean inCreateQueryMethod;

                    @Override
                    public void visitApply(JCTree.JCMethodInvocation jcMethodInvocation) {
                        Name name = getMethodName(jcMethodInvocation.getMethodSelect());
                        if (name != null && name.toString().equals("createQuery")) {
                            inCreateQueryMethod = true;
                            super.visitApply(jcMethodInvocation);
                            inCreateQueryMethod = false;
                        }
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
                                    SessionFactoryImplementor factory = new JavacSessionFactory();
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
                                    catch (Exception e) {
                                        handler.reportError(e.getMessage());
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
