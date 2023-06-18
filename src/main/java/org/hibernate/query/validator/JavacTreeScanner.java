package org.hibernate.query.validator;

import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeScanner;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.util.HashSet;
import java.util.Set;

import static org.hibernate.query.validator.HQLProcessor.hibernate;
import static org.hibernate.query.validator.HQLProcessor.jpa;
import static org.hibernate.query.validator.Validation.validate;

/**
 * @author Gavin King
 */
class JavacTreeScanner extends TreeScanner {
    private final JavacChecker javacChecker;
    final Set<Integer> setParameterLabels;
    final Set<String> setParameterNames;
    final Set<String> setOrderBy;
    private final Element element;
    private final TypeElement panacheEntity;
    boolean immediatelyCalled;

    public JavacTreeScanner(JavacChecker javacChecker, Element element, TypeElement panacheEntity) {
        this.javacChecker = javacChecker;
        this.element = element;
        this.panacheEntity = panacheEntity;
        setParameterLabels = new HashSet<>();
        setParameterNames = new HashSet<>();
        setOrderBy = new HashSet<>();
    }

    private void check(JCTree.JCLiteral jcLiteral, String hql,
                       boolean inCreateQueryMethod) {
        JavacErrorReporter handler = new JavacErrorReporter(javacChecker.getJavacProcessor(), jcLiteral, element, hql);
        validate(hql, inCreateQueryMethod && immediatelyCalled,
                setParameterLabels, setParameterNames, handler,
                JavacProcessor.sessionFactory.make(javacChecker.getProcessingEnv()));
    }

    private void checkPanacheQuery(JCTree.JCLiteral jcLiteral, String targetType, String methodName, String panacheQl,
                                   com.sun.tools.javac.util.List<JCTree.JCExpression> args) {
        JavacErrorReporter handler =
                new JavacErrorReporter(javacChecker.getJavacProcessor(), jcLiteral, element, panacheQl);
        collectPanacheArguments(args);
        int[] offset = new int[1];
        String hql = PanacheUtils.panacheQlToHql(handler, targetType, methodName,
                panacheQl, offset, setParameterLabels, setOrderBy);
        if (hql == null)
            return;
        validate(hql, true,
                setParameterLabels, setParameterNames, handler,
                JavacProcessor.sessionFactory.make(javacChecker.getJavacProcessor().getProcessingEnv()),
                offset[0]);
    }

    private void collectPanacheArguments(com.sun.tools.javac.util.List<JCTree.JCExpression> args) {
        // first arg is pql
        // second arg can be Sort, Object..., Map or Parameters
        setParameterLabels.clear();
        setParameterNames.clear();
        setOrderBy.clear();
        com.sun.tools.javac.util.List<JCTree.JCExpression> nonQueryArgs = args.tail;
        if (!nonQueryArgs.isEmpty()) {
            if (isPanacheSortCall(nonQueryArgs.head)) {
                nonQueryArgs = nonQueryArgs.tail;
            }

            if (!nonQueryArgs.isEmpty()) {
                JCTree.JCExpression firstArg = nonQueryArgs.head;
                isParametersCall(firstArg);
                if (setParameterNames.isEmpty()) {
                    int i = 1;
                    for (JCTree.JCExpression arg : nonQueryArgs) {
                        setParameterLabels.add(i++);
                    }
                }
            }
        }
    }

    private boolean isParametersCall(JCTree.JCExpression firstArg) {
        if (firstArg.getKind() == Tree.Kind.METHOD_INVOCATION) {
            JCTree.JCMethodInvocation invocation = (JCTree.JCMethodInvocation) firstArg;
            JCTree.JCExpression method = invocation.meth;
            if (method.getKind() == Tree.Kind.MEMBER_SELECT) {
                JCTree.JCFieldAccess fa = (JCTree.JCFieldAccess) method;
                if (fa.name.toString().equals("and") && isParametersCall(fa.selected)) {
                    JCTree.JCLiteral queryArg = firstArgument(invocation);
                    if (queryArg != null && queryArg.value instanceof String) {
                        String name = (String) queryArg.value;
                        setParameterNames.add(name);
                        return true;
                    }
                } else if (fa.name.toString().equals("with")
                        && fa.selected.getKind() == Tree.Kind.IDENTIFIER) {
                    String target = ((JCTree.JCIdent) fa.selected).name.toString();
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

    private boolean isPanacheSortCall(JCTree.JCExpression firstArg) {
        if (firstArg.getKind() == Tree.Kind.METHOD_INVOCATION) {
            JCTree.JCMethodInvocation invocation = (JCTree.JCMethodInvocation) firstArg;
            JCTree.JCExpression method = invocation.meth;
            if (method.getKind() == Tree.Kind.MEMBER_SELECT) {
                JCTree.JCFieldAccess fa = (JCTree.JCFieldAccess) method;
                String fieldName = fa.name.toString();
                if ((fieldName.equals("and")
                        || fieldName.equals("descending")
                        || fieldName.equals("ascending")
                        || fieldName.equals("direction"))
                        && isPanacheSortCall(fa.selected)) {
                    for (JCTree.JCExpression e : invocation.args) {
                        if (e instanceof JCTree.JCLiteral) {
                            JCTree.JCLiteral lit = (JCTree.JCLiteral) e;
                            if (lit.value instanceof String) {
                                setOrderBy.add((String) lit.value);
                            }
                        }
                    }
                    return true;
                }
                else if ((fieldName.equals("by")
                        || fieldName.equals("descending")
                        || fieldName.equals("ascending"))
                        && fa.selected.getKind() == Tree.Kind.IDENTIFIER) {
                    String target = ((JCTree.JCIdent) fa.selected).name.toString();
                    if (target.equals("Sort")) {
                        for (JCTree.JCExpression e : invocation.args) {
                            if (e instanceof JCTree.JCLiteral) {
                                JCTree.JCLiteral lit = (JCTree.JCLiteral) e;
                                if (lit.value instanceof String) {
                                    setOrderBy.add((String) lit.value);
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

    private JCTree.JCLiteral firstArgument(JCTree.JCMethodInvocation call) {
        for (JCTree.JCExpression e : call.args) {
            return e instanceof JCTree.JCLiteral
                    ? (JCTree.JCLiteral) e
                    : null;
        }
        return null;
    }

    @Override
    public void visitApply(JCTree.JCMethodInvocation jcMethodInvocation) {
        String name = getMethodName(jcMethodInvocation.meth);
        switch (name) {
            case "getResultList":
            case "getSingleResult":
            case "getSingleResultOrNull":
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
                        if (queryArg != null
                                && queryArg.value instanceof String
                                && panacheEntity != null) {
                            String panacheQl = (String) queryArg.value;
                            String entityName = panacheEntity.getSimpleName().toString();
                            checkPanacheQuery(queryArg, entityName, name, panacheQl, jcMethodInvocation.args);
                        }
                        break;
                }
                super.visitApply(jcMethodInvocation); //needed!
                break;
            case "createQuery":
            case "createSelectionQuery":
            case "createMutationQuery":
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
        if (jpa("NamedQuery").equals(name)
                || hibernate("NamedQuery").equals(name)) {
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
        else if (hibernate("processing.HQL").equals(name)) {
            for (JCTree.JCExpression arg : jcAnnotation.args) {
                if (arg instanceof JCTree.JCAssign) {
                    JCTree.JCAssign assign = (JCTree.JCAssign) arg;
                    if ("value".equals(assign.lhs.toString())
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

}
