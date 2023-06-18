package org.hibernate.query.validator;

import org.eclipse.jdt.internal.compiler.ASTVisitor;
import org.eclipse.jdt.internal.compiler.Compiler;
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration;
import org.eclipse.jdt.internal.compiler.ast.Expression;
import org.eclipse.jdt.internal.compiler.ast.IntLiteral;
import org.eclipse.jdt.internal.compiler.ast.MemberValuePair;
import org.eclipse.jdt.internal.compiler.ast.MessageSend;
import org.eclipse.jdt.internal.compiler.ast.SingleNameReference;
import org.eclipse.jdt.internal.compiler.ast.StringLiteral;
import org.eclipse.jdt.internal.compiler.ast.ThisReference;
import org.eclipse.jdt.internal.compiler.lookup.BlockScope;

import javax.lang.model.element.TypeElement;
import java.util.HashSet;
import java.util.Set;

import static java.lang.Integer.parseInt;
import static org.eclipse.jdt.core.compiler.CharOperation.charToString;
import static org.hibernate.query.validator.ECJSessionFactory.qualifiedName;
import static org.hibernate.query.validator.HQLProcessor.hibernate;
import static org.hibernate.query.validator.HQLProcessor.jpa;
import static org.hibernate.query.validator.Validation.validate;

/**
 * @author Gavin King
 */
class ECJASTVisitor extends ASTVisitor {
    final Set<Integer> setParameterLabels;
    final Set<String> setParameterNames;
    final Set<String> setOrderBy;
    private final TypeElement panacheEntity;
    private final CompilationUnitDeclaration unit;
    private final Compiler compiler;
    boolean immediatelyCalled;

    public ECJASTVisitor(TypeElement panacheEntity, CompilationUnitDeclaration unit, Compiler compiler) {
        this.panacheEntity = panacheEntity;
        this.unit = unit;
        this.compiler = compiler;
        setParameterLabels = new HashSet<>();
        setParameterNames = new HashSet<>();
        setOrderBy = new HashSet<>();
    }

    @Override
    public boolean visit(MessageSend messageSend, BlockScope scope) {
        String name = charToString(messageSend.selector);
        switch (name) {
            case "getResultList":
            case "getSingleResult":
            case "getSingleResultOrNull":
                immediatelyCalled = true;
                break;
            case "count":
            case "delete":
            case "update":
            case "exists":
            case "stream":
            case "list":
            case "find":
                // Disable until we can make this type-safe for Javac
//                                if (messageSend.receiver instanceof SingleNameReference) {
//                                    SingleNameReference ref = (SingleNameReference) messageSend.receiver;
//                                    String target = charToString(ref.token);
//                                    StringLiteral queryArg = firstArgument(messageSend);
//                                    if (queryArg != null) {
//                                        checkPanacheQuery(queryArg, target, name, charToString(queryArg.source()), messageSend.arguments);
//                                    }
                if (messageSend.receiver instanceof ThisReference && panacheEntity != null) {
                    String target = panacheEntity.getSimpleName().toString();
                    StringLiteral queryArg = firstArgument(messageSend);
                    if (queryArg != null) {
                        String panacheQl = charToString(queryArg.source());
                        checkPanacheQuery(queryArg, target, name, panacheQl, messageSend.arguments);
                    }
                }
                break;
            case "createQuery":
            case "createSelectionQuery":
            case "createMutationQuery":
                for (Expression argument : messageSend.arguments) {
                    if (argument instanceof StringLiteral) {
                        check((StringLiteral) argument, true);
                    }
                    break;
                }
                break;
            case "setParameter":
                for (Expression argument : messageSend.arguments) {
                    if (argument instanceof StringLiteral) {
                        String paramName =
                                charToString(((StringLiteral) argument)
                                        .source());
                        setParameterNames.add(paramName);
                    } else if (argument instanceof IntLiteral) {
                        int paramLabel = parseInt(new String(((IntLiteral) argument).source()));
                        setParameterLabels.add(paramLabel);
                    }
                    //the remaining parameters aren't parameter ids!
                    break;
                }

                break;
        }
        return true;
    }

    private StringLiteral firstArgument(MessageSend messageSend) {
        for (Expression argument : messageSend.arguments) {
            if (argument instanceof StringLiteral) {
                return (StringLiteral) argument;
            }
        }
        return null;
    }

    @Override
    public void endVisit(MessageSend messageSend, BlockScope scope) {
        String name = charToString(messageSend.selector);
        switch (name) {
            case "getResultList":
            case "getSingleResult":
                immediatelyCalled = false;
                break;
        }
    }

    @Override
    public boolean visit(MemberValuePair pair, BlockScope scope) {
        String qualifiedName = qualifiedName(pair.binding);
        if (qualifiedName.equals(jpa("NamedQuery.query"))
                || qualifiedName.equals(hibernate("NamedQuery.query"))
                || qualifiedName.equals(hibernate("processing.HQL.value"))) {
            if (pair.value instanceof StringLiteral) {
                check((StringLiteral) pair.value, false);
            }
        }
        return true;
    }

    void check(StringLiteral stringLiteral, boolean inCreateQueryMethod) {
        String hql = charToString(stringLiteral.source());
        ECJErrorReporter handler = new ECJErrorReporter(stringLiteral, unit, compiler, hql);
        validate(hql, inCreateQueryMethod && immediatelyCalled,
                setParameterLabels, setParameterNames, handler,
                ECJProcessor.sessionFactory.make(unit));
    }

    void checkPanacheQuery(StringLiteral stringLiteral, String targetType, String methodName,
                           String panacheQl, Expression[] args) {
        ECJErrorReporter handler = new ECJErrorReporter(stringLiteral, unit, compiler, panacheQl);
        collectPanacheArguments(args);
        int[] offset = new int[1];
        String hql = PanacheUtils.panacheQlToHql(handler, targetType, methodName,
                panacheQl, offset, setParameterLabels, setOrderBy);
        if (hql != null) {
            validate(hql, true,
                    setParameterLabels, setParameterNames, handler,
                    ECJProcessor.sessionFactory.make(unit), offset[0]);
        }
    }

    private void collectPanacheArguments(Expression[] args) {
        // first arg is pql
        // second arg can be Sort, Object..., Map or Parameters
        setParameterLabels.clear();
        setParameterNames.clear();
        setOrderBy.clear();
        if (args.length > 1) {
            int firstArgIndex = 1;
            if (isPanacheSortCall(args[firstArgIndex])) {
                firstArgIndex++;
            }

            if (args.length > firstArgIndex) {
                Expression firstArg = args[firstArgIndex];
                isParametersCall(firstArg);
                if (setParameterNames.isEmpty()) {
                    for (int i = 0; i < args.length - firstArgIndex; i++) {
                        setParameterLabels.add(1 + i);
                    }
                }
            }
        }
    }

    private boolean isParametersCall(Expression firstArg) {
        if (firstArg instanceof MessageSend) {
            MessageSend invocation = (MessageSend) firstArg;
            String fieldName = charToString(invocation.selector);
            if (fieldName.equals("and") && isParametersCall(invocation.receiver)) {
                StringLiteral queryArg = firstArgument(invocation);
                if (queryArg != null) {
                    setParameterNames.add(charToString(queryArg.source()));
                    return true;
                }
            }
            else if (fieldName.equals("with")
                    && invocation.receiver instanceof SingleNameReference) {
                SingleNameReference receiver = (SingleNameReference) invocation.receiver;
                String target = charToString(receiver.token);
                if (target.equals("Parameters")) {
                    StringLiteral queryArg = firstArgument(invocation);
                    if (queryArg != null) {
                        setParameterNames.add(charToString(queryArg.source()));
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isPanacheSortCall(Expression firstArg) {
        if (firstArg instanceof MessageSend) {
            MessageSend invocation = (MessageSend) firstArg;
            String fieldName = charToString(invocation.selector);
            if ((fieldName.equals("and")
                    || fieldName.equals("descending")
                    || fieldName.equals("ascending")
                    || fieldName.equals("direction"))
                    && isPanacheSortCall(invocation.receiver)) {
                for (Expression e : invocation.arguments) {
                    if (e instanceof StringLiteral) {
                        StringLiteral lit = (StringLiteral) e;
                        setOrderBy.add(charToString(lit.source()));
                    }
                }
                return true;
            }
            else if ((fieldName.equals("by")
                    || fieldName.equals("descending")
                    || fieldName.equals("ascending"))
                    && invocation.receiver instanceof SingleNameReference) {
                SingleNameReference receiver = (SingleNameReference) invocation.receiver;
                String target = charToString(receiver.token);
                if (target.equals("Sort")) {
                    for (Expression e : invocation.arguments) {
                        if (e instanceof StringLiteral) {
                            StringLiteral lit = (StringLiteral) e;
                            setOrderBy.add(charToString(lit.source()));
                        }
                    }
                    return true;
                }
            }
        }
        return false;
    }
}
