package org.hibernate.query.validator;

import static java.lang.Integer.parseInt;
import static java.util.Collections.emptyList;
import static org.eclipse.jdt.core.compiler.CharOperation.charToString;
import static org.eclipse.jdt.internal.compiler.util.Util.getLineNumber;
import static org.eclipse.jdt.internal.compiler.util.Util.searchColumnNumber;
import static org.hibernate.query.validator.ECJSessionFactory.getAnnotation;
import static org.hibernate.query.validator.ECJSessionFactory.qualifiedName;
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
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

import org.eclipse.jdt.core.compiler.CategorizedProblem;
import org.eclipse.jdt.internal.compiler.ASTVisitor;
import org.eclipse.jdt.internal.compiler.CompilationResult;
import org.eclipse.jdt.internal.compiler.Compiler;
import org.eclipse.jdt.internal.compiler.apt.dispatch.BaseProcessingEnvImpl;
import org.eclipse.jdt.internal.compiler.ast.ASTNode;
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration;
import org.eclipse.jdt.internal.compiler.ast.Expression;
import org.eclipse.jdt.internal.compiler.ast.IntLiteral;
import org.eclipse.jdt.internal.compiler.ast.MemberValuePair;
import org.eclipse.jdt.internal.compiler.ast.MessageSend;
import org.eclipse.jdt.internal.compiler.ast.SingleNameReference;
import org.eclipse.jdt.internal.compiler.ast.StringLiteral;
import org.eclipse.jdt.internal.compiler.ast.ThisReference;
import org.eclipse.jdt.internal.compiler.ast.TypeDeclaration;
import org.eclipse.jdt.internal.compiler.impl.StringConstant;
import org.eclipse.jdt.internal.compiler.lookup.AnnotationBinding;
import org.eclipse.jdt.internal.compiler.lookup.BinaryTypeBinding;
import org.eclipse.jdt.internal.compiler.lookup.Binding;
import org.eclipse.jdt.internal.compiler.lookup.BlockScope;
import org.eclipse.jdt.internal.compiler.lookup.ElementValuePair;
import org.eclipse.jdt.internal.compiler.lookup.TypeBinding;
import org.eclipse.jdt.internal.compiler.problem.ProblemSeverities;
import org.hibernate.QueryException;
import org.hibernate.dialect.Dialect;

import antlr.RecognitionException;

/**
 * Annotation processor that validates HQL and JPQL queries
 * for ECJ.
 *
 * @see CheckHQL
 *
 * @author Gavin King
 */
//@SupportedAnnotationTypes(CHECK_HQL)
public class ECJProcessor extends AbstractProcessor {

    static Mocker<ECJSessionFactory> sessionFactory = Mocker.variadic(ECJSessionFactory.class);

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Compiler compiler = ((BaseProcessingEnvImpl) processingEnv).getCompiler();
        if (!roundEnv.getRootElements().isEmpty()) {
            for (CompilationUnitDeclaration unit : compiler.unitsToProcess) {
                compiler.parser.getMethodBodies(unit);
                checkHQL(unit, compiler);
            }
        }
        return true;
    }

    private void checkHQL(CompilationUnitDeclaration unit, Compiler compiler) {
        for (TypeDeclaration type : unit.types) {
            if (isCheckable(type.binding, unit)) {
                List<String> whitelist = getWhitelist(type.binding, unit, compiler);
                Elements elements = processingEnv.getElementUtils();
                TypeElement typeElement = elements.getTypeElement(qualifiedName(type.binding));
                TypeElement panacheEntity = PanacheUtils.isPanache(typeElement, processingEnv.getTypeUtils(), elements);
                type.traverse(new ASTVisitor() {
                    final Set<Integer> setParameterLabels = new HashSet<>();
                    final Set<String> setParameterNames = new HashSet<>();
                    final Set<String> setOrderBy = new HashSet<>();
                    boolean immediatelyCalled;

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
                                        checkPanacheQuery(queryArg, target, name, charToString(queryArg.source()), messageSend.arguments);
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
                                    }
                                    else if (argument instanceof IntLiteral) {
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
                        if (qualifiedName(pair.binding)
                                .equals(jpa("NamedQuery.query"))) {
                            if (pair.value instanceof StringLiteral) {
                                check((StringLiteral) pair.value, false);
                            }
                        }
                        return true;
                    }

                    void check(StringLiteral stringLiteral, boolean inCreateQueryMethod) {
                        String hql = charToString(stringLiteral.source());
                        ErrorReporter handler = new ErrorReporter(stringLiteral, unit, compiler);
                        validate(hql, inCreateQueryMethod && immediatelyCalled,
                                setParameterLabels, setParameterNames, handler,
                                sessionFactory.make(whitelist, handler, unit));
                    }
                    
                    void checkPanacheQuery(StringLiteral stringLiteral, String targetType, String methodName, String panacheQl, Expression[] args) {
                        ErrorReporter handler = new ErrorReporter(stringLiteral, unit, compiler);
                        collectPanacheArguments(args);
                        int[] offset = new int[1];
                        String hql = PanacheUtils.panacheQlToHql(handler, targetType, methodName, 
                                                                 panacheQl, offset, setParameterLabels, setOrderBy);
                        if (hql == null)
                            return;
                        validate(hql, true,
                                 setParameterLabels, setParameterNames, handler,
                                 sessionFactory.make(whitelist, handler, unit), offset[0]);
                    }

                    private void collectPanacheArguments(Expression[] args) {
                        // first arg is pql
                        // second arg can be Sort, Object..., Map or Parameters
                        setParameterLabels.clear();
                        setParameterNames.clear();
                        setOrderBy.clear();
                        if (args.length > 1) {
                            int firstArgIndex = 1;
                            if (isSortCall(args[firstArgIndex])) {
                                firstArgIndex++;
                            }
                            
                            if (args.length > firstArgIndex) {
                                Expression firstArg = args[firstArgIndex];
                                isParametersCall(firstArg);
                                if (setParameterNames.isEmpty()) {
                                    for (int i = 0 ; i < args.length - firstArgIndex ; i++) {
                                        setParameterLabels.add(1 + i);
                                    }
                                }
                            }
                        }
                    }
                    private boolean isParametersCall(Expression firstArg) {
                        if (firstArg instanceof MessageSend) {
                            MessageSend invocation = (MessageSend)firstArg;
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

                    private boolean isSortCall(Expression firstArg) {
                        if (firstArg instanceof MessageSend) {
                            MessageSend invocation = (MessageSend)firstArg;
                            String fieldName = charToString(invocation.selector);
                                if ((fieldName.equals("and")
                                        || fieldName.equals("descending")
                                        || fieldName.equals("ascending")
                                        || fieldName.equals("direction"))
                                        && isSortCall(invocation.receiver)) {
                                    for (Expression e : invocation.arguments) {
                                        if (e instanceof StringLiteral) {
                                            StringLiteral lit = (StringLiteral)e;
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
                                                StringLiteral lit = (StringLiteral)e;
                                                setOrderBy.add(charToString(lit.source()));
                                            }
                                        }
                                        return true;
                                    }
                                }
                        }
                        return false;
                    }
                }, unit.scope);
            }
        }
    }

    private static boolean isCheckable(TypeBinding type, CompilationUnitDeclaration unit) {
        return getCheckAnnotation(type, unit)!=null;
    }

    private static List<String> getWhitelist(TypeBinding type,
                                             CompilationUnitDeclaration unit,
                                             Compiler compiler) {
        ElementValuePair[] members =
                getCheckAnnotation(type, unit).getElementValuePairs();
        if (members==null || members.length==0) {
            return emptyList();
        }
        List<String> names = new ArrayList<>();
        for (ElementValuePair pair: members) {
            Object value = pair.value;
            if (value instanceof Object[]) {
                for (Object literal : (Object[]) value) {
                    if (literal instanceof StringConstant) {
                        names.add(((StringConstant) literal).stringValue());
                    }
                }
            }
            else if (value instanceof StringConstant) {
                names.add(((StringConstant) value).stringValue());
            }
            else if (value instanceof BinaryTypeBinding) {
                String name = qualifiedName((BinaryTypeBinding) value);
                Dialect dialect;
                try {
                    dialect = (Dialect) Class.forName(name).newInstance();
                }
                catch (Exception e) {
                    //TODO: this error doesn't have location info!!
                    new ErrorReporter(null, unit, compiler)
                            .reportError("could not create dialect " + name);
                    continue;
                }
                names.addAll(dialect.getFunctions().keySet());
            }
        }
        return names;
    }

    private static AnnotationBinding getCheckAnnotation(TypeBinding type,
                                                        CompilationUnitDeclaration unit) {
        AnnotationBinding result = getAnnotation(type, CHECK_HQL);
        if (result!=null) return result;
        Binding packInfo = unit.scope.getType("package-info".toCharArray());
        return getAnnotation(packInfo, CHECK_HQL);
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    static class ErrorReporter implements Validation.Handler {

        private ASTNode node;
        private CompilationUnitDeclaration unit;
        private Compiler compiler;

        ErrorReporter(ASTNode node,
                      CompilationUnitDeclaration unit,
                      Compiler compiler) {
            this.node = node;
            this.unit = unit;
            this.compiler = compiler;
        }

        @Override
        public int getErrorCount() {
            return 0;
        }

        @Override
        public void throwQueryException() throws QueryException {}

        @Override
        public void error(int start, int end, String message) {
            report(ProblemSeverities.Error, message, start, end);
        }

        @Override
        public void warn(int start, int end, String message) {
            report(ProblemSeverities.Warning, message, start, end);
        }

        private void report(int severity, String message, int offset, int endOffset) {
            CompilationResult result = unit.compilationResult();
            char[] fileName = result.fileName;
            int[] lineEnds = result.getLineSeparatorPositions();
            int startPosition;
            int endPosition;
            if (node!=null) {
                startPosition = node.sourceStart + offset;
                endPosition = endOffset < 0 ?
                        node.sourceEnd - 1 :
                        node.sourceStart + endOffset;
            }
            else {
                startPosition = 0;
                endPosition = 0;
            }
            int lineNumber = startPosition >= 0
                    ? getLineNumber(startPosition, lineEnds, 0, lineEnds.length - 1)
                    : 0;
            int columnNumber = startPosition >= 0
                    ? searchColumnNumber(lineEnds, lineNumber, startPosition)
                    : 0;

            CategorizedProblem problem =
                    compiler.problemReporter.problemFactory
                            .createProblem(fileName, 0,
                                    new String[]{message},
                                    new String[]{message},
                                    severity,
                                    startPosition, endPosition,
                                    lineNumber, columnNumber);
            compiler.problemReporter.record(problem, result, unit, true);
        }

        @Override
        public void reportError(RecognitionException e) {
            report(ProblemSeverities.Error, e.getMessage(), e.getColumn(), -1);
        }

        @Override
        public void reportError(String text) {
            report(ProblemSeverities.Error, text, 1, -1);
        }

        @Override
        public void reportWarning(String text) {
            report(ProblemSeverities.Warning, text, 1, -1);
        }

    }

}
