package org.hibernate.query.validator;

import static org.hibernate.query.validator.HQLProcessor.CHECK_HQL;
import static org.hibernate.query.validator.HQLProcessor.jpa;
import static org.hibernate.query.validator.Validation.validate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

import org.hibernate.QueryException;
import org.hibernate.dialect.Dialect;

import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.Tree.Kind;
import com.sun.tools.javac.code.Attribute;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCFieldAccess;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Pair;

import antlr.RecognitionException;

/**
 * Annotation processor that validates HQL and JPQL queries
 * for `javac`.
 *
 * @see CheckHQL
 */
//@SupportedAnnotationTypes(CHECK_HQL)
public class JavacProcessor extends AbstractProcessor {

    static Mocker<JavacSessionFactory> sessionFactory = Mocker.variadic(JavacSessionFactory.class);

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getRootElements()) {
            if (element instanceof PackageElement) {
//                for (Element member : element.getEnclosedElements()) {
//                    checkHQL(member);
//                }
            }
            else {
                checkHQL(element);
            }
        }
        return false;
    }

    private void checkHQL(Element element) {
        Elements elementUtils = processingEnv.getElementUtils();
        if (isCheckable(element) || isCheckable(element.getEnclosingElement())) {
            List<String> whitelist = getWhitelist(element);
            JCTree tree = ((JavacElements) elementUtils).getTree(element);
            TypeElement panacheEntity = PanacheUtils.isPanache(element, processingEnv.getTypeUtils(), elementUtils);
            if (tree != null) {
                tree.accept(new TreeScanner() {
                    Set<Integer> setParameterLabels = new HashSet<>();
                    Set<String> setParameterNames = new HashSet<>();
                    Set<String> setOrderBy = new HashSet<>();
                    boolean immediatelyCalled;

                    private void check(JCTree.JCLiteral jcLiteral, String hql,
                                       boolean inCreateQueryMethod) {
                        ErrorReporter handler = new ErrorReporter(jcLiteral, element);
                        validate(hql, inCreateQueryMethod && immediatelyCalled,
                                setParameterLabels, setParameterNames, handler,
                                sessionFactory.make(whitelist, handler, processingEnv));
                    }

                    private void checkPanacheQuery(JCTree.JCLiteral jcLiteral, String targetType, String methodName, String panacheQl,
                                       com.sun.tools.javac.util.List<JCExpression> args) {
                        ErrorReporter handler = new ErrorReporter(jcLiteral, element);
                        collectPanacheArguments(args);
                        int[] offset = new int[1];
                        String hql = PanacheUtils.panacheQlToHql(handler, targetType, methodName, 
                                                                 panacheQl, offset, setParameterLabels, setOrderBy);
                        if (hql == null)
                            return;
                        validate(hql, true,
                                setParameterLabels, setParameterNames, handler,
                                sessionFactory.make(whitelist, handler, processingEnv), offset[0]);
                    }

                    private void collectPanacheArguments(com.sun.tools.javac.util.List<JCExpression> args) {
                        // first arg is pql
                        // second arg can be Sort, Object..., Map or Parameters
                        setParameterLabels.clear();
                        setParameterNames.clear();
                        setOrderBy.clear();
                        com.sun.tools.javac.util.List<JCExpression> nonQueryArgs = args.tail;
                        if (!nonQueryArgs.isEmpty()) {
                            if (isSortCall(nonQueryArgs.head)) {
                                nonQueryArgs = nonQueryArgs.tail;
                            }
                            
                            if (!nonQueryArgs.isEmpty()) {
                                JCExpression firstArg = nonQueryArgs.head;
                                isParametersCall(firstArg);
                                if (setParameterNames.isEmpty()) {
                                    int i = 1;
                                    for (JCExpression arg : nonQueryArgs) {
                                        setParameterLabels.add(i++);
                                    }
                                }
                            }
                        }
                    }

                    private boolean isParametersCall(JCExpression firstArg) {
                        if (firstArg.getKind() == Kind.METHOD_INVOCATION) {
                            JCTree.JCMethodInvocation invocation = (JCTree.JCMethodInvocation)firstArg;
                            JCExpression method = invocation.meth;
                            if (method.getKind() == Kind.MEMBER_SELECT) {
                                JCTree.JCFieldAccess fa = (JCFieldAccess) method;
                                if (fa.name.toString().equals("and") && isParametersCall(fa.selected)) {
                                    JCTree.JCLiteral queryArg = firstArgument(invocation);
                                    if (queryArg != null && queryArg.value instanceof String) {
                                        String name = (String) queryArg.value;
                                        setParameterNames.add(name);
                                        return true;
                                    }
                                }
                                else if (fa.name.toString().equals("with")
                                        && fa.selected.getKind() == Kind.IDENTIFIER) {
                                    String target = ((JCTree.JCIdent)fa.selected).name.toString();
                                    if (target.equals("Parameters")) {
                                        JCTree.JCLiteral queryArg = firstArgument(invocation);
                                        if (queryArg != null && queryArg.value instanceof String) {
                                            String name = (String) queryArg.value;
                                            setParameterNames.add(name);
                                            return true;
                                        }
                                    }
                                }
                            }
                        }
                        return false;
                    }

                    private boolean isSortCall(JCExpression firstArg) {
                        if (firstArg.getKind() == Kind.METHOD_INVOCATION) {
                            JCTree.JCMethodInvocation invocation = (JCTree.JCMethodInvocation)firstArg;
                            JCExpression method = invocation.meth;
                            if (method.getKind() == Kind.MEMBER_SELECT) {
                                JCTree.JCFieldAccess fa = (JCFieldAccess) method;
                                String fieldName = fa.name.toString();
                                if ((fieldName.equals("and")
                                        || fieldName.equals("descending")
                                        || fieldName.equals("ascending")
                                        || fieldName.equals("direction"))
                                        && isSortCall(fa.selected)) {
                                    for (JCTree.JCExpression e : invocation.args) {
                                        if (e instanceof JCTree.JCLiteral) {
                                            JCTree.JCLiteral lit = (JCTree.JCLiteral)e;
                                            if (lit.value instanceof String) {
                                                setOrderBy.add((String)lit.value);
                                            }
                                        }
                                    }
                                    return true;
                                }
                                else if ((fieldName.equals("by")
                                        || fieldName.equals("descending")
                                        || fieldName.equals("ascending"))
                                        && fa.selected.getKind() == Kind.IDENTIFIER) {
                                    String target = ((JCTree.JCIdent)fa.selected).name.toString();
                                    if (target.equals("Sort")) {
                                        for (JCTree.JCExpression e : invocation.args) {
                                            if (e instanceof JCTree.JCLiteral) {
                                                JCTree.JCLiteral lit = (JCTree.JCLiteral)e;
                                                if (lit.value instanceof String) {
                                                    setOrderBy.add((String)lit.value);
                                                }
                                            }
                                        }
                                        return true;
                                    }
                                }
                            }
                        }
                        return false;
                    }

                    JCTree.JCLiteral firstArgument(JCTree.JCMethodInvocation call) {
                        for (JCTree.JCExpression e : call.args) {
                            return e instanceof JCTree.JCLiteral ?
                                    (JCTree.JCLiteral) e : null;
                        }
                        return null;
                    }

                    @Override
                    public void visitApply(JCTree.JCMethodInvocation jcMethodInvocation) {
                        String name = getMethodName(jcMethodInvocation.meth);
                        switch (name) {
                            case "getResultList":
                            case "getSingleResult":
                                immediatelyCalled = true;
                                super.visitApply(jcMethodInvocation);
                                immediatelyCalled = false;
                                break;
                            case "count":
                            case "delete":
                            case "update":
                            case "exists":
                            case "stream":
                            case "list":
                            case "find":
                                switch (jcMethodInvocation.meth.getKind()) {
                                // disable this until we figure out how to type the LHS
//                                case MEMBER_SELECT:
//                                    JCTree.JCFieldAccess fa = (JCFieldAccess) jcMethodInvocation.meth;
//                                    switch (fa.selected.getKind()) {
//                                    case IDENTIFIER:
//                                        JCTree.JCIdent target = (JCIdent) fa.selected;
//                                        JCTree.JCLiteral queryArg = firstArgument(jcMethodInvocation);
//                                        if (queryArg != null && queryArg.value instanceof String) {
//                                            String panacheQl = (String) queryArg.value;
//                                            checkPanacheQuery(queryArg, target.name.toString(), name, panacheQl, jcMethodInvocation.args);
//                                        }
//                                        break;
//                                    }
//                                    break;
                                case IDENTIFIER:
                                    JCTree.JCLiteral queryArg = firstArgument(jcMethodInvocation);
                                    if (queryArg != null && queryArg.value instanceof String && panacheEntity != null) {
                                        String panacheQl = (String) queryArg.value;
                                        checkPanacheQuery(queryArg, panacheEntity.getSimpleName().toString(), name, panacheQl, jcMethodInvocation.args);
                                    }
                                    break;
                                }
                                super.visitApply(jcMethodInvocation); //needed!
                                break;
                            case "createQuery":
                                JCTree.JCLiteral queryArg = firstArgument(jcMethodInvocation);
                                if (queryArg != null && queryArg.value instanceof String) {
                                    String hql = (String) queryArg.value;
                                    check(queryArg, hql, true);
                                }
                                super.visitApply(jcMethodInvocation);
                                break;
                            case "setParameter":
                                JCTree.JCLiteral paramArg = firstArgument(jcMethodInvocation);
                                if (paramArg != null) {
                                    if (paramArg.value instanceof String) {
                                        setParameterNames.add((String) paramArg.value);
                                    }
                                    else if (paramArg.value instanceof Integer) {
                                        setParameterLabels.add((Integer) paramArg.value);
                                    }
                                }
                                super.visitApply(jcMethodInvocation);
                                break;
                            default:
                                super.visitApply(jcMethodInvocation); //needed!
                                break;
                        }
                    }

                    @Override
                    public void visitAnnotation(JCTree.JCAnnotation jcAnnotation) {
                        AnnotationMirror annotation = jcAnnotation.attribute;
                        String name = annotation.getAnnotationType().toString();
                        if (name.equals(jpa("NamedQuery"))) {
                            for (JCTree.JCExpression arg : jcAnnotation.args) {
                                if (arg instanceof JCTree.JCAssign) {
                                    JCTree.JCAssign assign = (JCTree.JCAssign) arg;
                                    if ("query".equals(assign.lhs.toString())
                                            && assign.rhs instanceof JCTree.JCLiteral) {
                                        JCTree.JCLiteral jcLiteral =
                                                (JCTree.JCLiteral) assign.rhs;
                                        if (jcLiteral.value instanceof String) {
                                            check(jcLiteral, (String) jcLiteral.value, false);
                                        }
                                    }
                                }
                            }
                        }
                        else {
                            super.visitAnnotation(jcAnnotation); //needed!
                        }
                    }

                });
            }
        }
    }

    private static boolean isCheckAnnotation(AnnotationMirror am) {
        return am.getAnnotationType().asElement().toString().equals(CHECK_HQL);
    }

    private static boolean isCheckable(Element element) {
        for (AnnotationMirror am: element.getAnnotationMirrors()) {
            if (isCheckAnnotation(am)) {
                return true;
            }
        }
        return false;
    }

    private List<String> getWhitelist(Element element) {
        List<String> list = new ArrayList<>();
        element.getAnnotationMirrors().forEach(am -> {
            if (isCheckAnnotation(am)) {
                am.getElementValues().forEach((var, act) -> {
                    switch (var.getSimpleName().toString()) {
                        case "whitelist":
                            if (act instanceof Attribute.Array) {
                                for (Attribute a: ((Attribute.Array) act).values) {
                                    Object value = a.getValue();
                                    if (value instanceof String) {
                                        list.add(value.toString());
                                    }
                                }
                            }
                            break;
                        case "dialect":
                            if (act instanceof Attribute.Class) {
                                String name = act.getValue().toString().replace(".class","");
                                try {
                                    Dialect dialect = (Dialect) Class.forName(name).newInstance();
                                    list.addAll(dialect.getFunctions().keySet());
                                }
                                catch (Exception e2) {
                                    processingEnv.getMessager()
                                            .printMessage(Diagnostic.Kind.ERROR,
                                                    "could not create dialect " + name,
                                                    element, am, act);
                                }
                            }
                            break;
                    }
                });
            }
        });
        return list;
    }

    private static String getMethodName(ExpressionTree select) {
        if (select instanceof MemberSelectTree) {
            MemberSelectTree ref = (MemberSelectTree) select;
            return ref.getIdentifier().toString();
        }
        else if (select instanceof IdentifierTree) {
            IdentifierTree ref = (IdentifierTree) select;
            return ref.getName().toString();
        }
        else {
            return null;
        }
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    class ErrorReporter implements Validation.Handler {

        private static final String KEY = "proc.messager";

        private Log log;
        private JCTree.JCLiteral literal;

        ErrorReporter(JCTree.JCLiteral literal, Element element) {
            this.literal = literal;

            Context context = getContext();
            log = Log.instance(context);
            Pair<JCTree, JCTree.JCCompilationUnit> pair = JavacElements.instance(context)
                    .getTreeAndTopLevel(element, null, null);
            JavaFileObject sourcefile = pair == null ? null :
                    pair.snd.sourcefile;
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
        public void error(int start, int end, String message) {
            log.error(literal.pos + start, KEY, message);
        }

        @Override
        public void warn(int start, int end, String message) {
            log.warning(literal.pos + start, KEY, message);
        }

        @Override
        public void reportError(RecognitionException e) {
            log.error(literal.pos + e.column, KEY, e.getMessage());
        }

        @Override
        public void reportError(String text) {
            log.error(literal, KEY, text);
        }

        @Override
        public void reportWarning(String text) {
            log.warning(literal, KEY, text);
        }

    }

    private Context getContext() {
        return ((JavacProcessingEnvironment) processingEnv).getContext();
    }
}
