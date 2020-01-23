package org.hibernate.query.validator;

import antlr.RecognitionException;
import org.eclipse.jdt.core.compiler.CategorizedProblem;
import org.eclipse.jdt.internal.compiler.ASTVisitor;
import org.eclipse.jdt.internal.compiler.CompilationResult;
import org.eclipse.jdt.internal.compiler.Compiler;
import org.eclipse.jdt.internal.compiler.apt.dispatch.BaseProcessingEnvImpl;
import org.eclipse.jdt.internal.compiler.ast.*;
import org.eclipse.jdt.internal.compiler.impl.StringConstant;
import org.eclipse.jdt.internal.compiler.lookup.*;
import org.eclipse.jdt.internal.compiler.problem.ProblemSeverities;
import org.hibernate.QueryException;
import org.hibernate.dialect.Dialect;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

/**
 * Annotation processor that validates HQL and JPQL queries
 * for ECJ.
 *
 * @see CheckHQL
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
                type.traverse(new ASTVisitor() {
                    Set<Integer> setParameterLabels = new HashSet<>();
                    Set<String> setParameterNames = new HashSet<>();
                    boolean immediatelyCalled;

                    @Override
                    public boolean visit(MessageSend messageSend, BlockScope scope) {
                        String name = charToString(messageSend.selector);
                        switch (name) {
                            case "getResultList":
                            case "getSingleResult":
                                immediatelyCalled = true;
                                break;
                            case "createQuery":
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
                    dialect = (Dialect) Class.forName(name)
                            .getDeclaredConstructor().newInstance();
                } catch (Exception e) {
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
