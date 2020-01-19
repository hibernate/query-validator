package org.hibernate.query.validator

import antlr.RecognitionException
import org.eclipse.jdt.internal.compiler.ast.Expression
import org.eclipse.jdt.internal.compiler.ast.NormalAnnotation
import org.eclipse.jdt.internal.compiler.ast.SingleMemberAnnotation
import org.eclipse.jdt.internal.compiler.ast.StringLiteral
import org.hibernate.QueryException

import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.TypeElement

import static java.lang.Integer.parseInt
import static java.util.Collections.emptyList
import static org.hibernate.query.validator.EclipseSessionFactory.*
import static org.hibernate.query.validator.HQLProcessor.CHECK_HQL
import static org.hibernate.query.validator.HQLProcessor.SPRING_QUERY_ANNOTATION
import static org.hibernate.query.validator.HQLProcessor.jpa
import static org.hibernate.query.validator.Validation.validate

/**
 * Annotation processor that validates HQL and JPQL queries
 * for Eclipse.
 *
 * @see CheckHQL
 */
//@SupportedAnnotationTypes(CHECK_HQL)
class EclipseProcessor extends AbstractProcessor {

    @Override
    boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        def compiler = processingEnv.getCompiler()
        if (!roundEnv.getRootElements().isEmpty()) {
            for (unit in compiler.unitsToProcess) {
                compiler.parser.getMethodBodies(unit)
                new Checker(unit, compiler).checkHQL()
            }
        }
        return false
    }

    private static boolean isCheckable(type, unit) {
        return getCheckAnnotation(type, unit)!=null
    }

    private static List<String> getWhitelist(type, unit, compiler) {
        def members = getCheckAnnotation(type, unit).getElementValuePairs()
        if (members==null || members.length==0) {
            return emptyList()
        }
        List<String> names = new ArrayList<>()
        for (pair in members) {
            def value = pair.value
            if (value instanceof Object[]) {
                for (literal in (Object[]) value) {
                    if (literal.class.simpleName == "StringConstant") {
                        names.add(literal.stringValue())
                    }
                }
            } else if (value.class.simpleName == "StringConstant") {
                names.add(value.stringValue())
            } else if (value.class.simpleName == "BinaryTypeBinding") {
                String name = qualifiedTypeName(value)
                def dialect
                try {
                    dialect = Class.forName(name).newInstance()
                    dialect.getFunctions()
                } catch (Exception e) {
                    try {
                        dialect = Class.forName(shadow(name)).newInstance()
                        dialect.getFunctions()
                    } catch (Exception e2) {
                        //TODO: this error doesn't have location info!!
                        new ErrorReporter(null, unit, compiler)
                                .reportError("could not create dialect " + name);
                        continue
                    }
                }
                names.addAll(dialect.getFunctions().keySet())
            }
        }
        return names
    }

    private final static String ORG_HIBERNATE =
            new StringBuilder("org.")
                    .append("hibernate.")
                    .toString()

    private static String shadow(String name) {
        return name.replace(ORG_HIBERNATE + "dialect",
                ORG_HIBERNATE + "query.validator.hibernate.dialect")
    }

    private static def getCheckAnnotation(type, unit) {
        def result = getAnnotation(type, CHECK_HQL)
        if (result!=null) return result
        def packInfo = unit.scope.getType("package-info".toCharArray())
        return getAnnotation(packInfo, CHECK_HQL)
    }

    @Override
    SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported()
    }

    static class Checker {

        Set<Integer> setParameterLabels = new HashSet<>()
        Set<String> setParameterNames = new HashSet<>()
        boolean immediatelyCalled = false

        private def unit
        private def compiler
        private List<String> whitelist

        Checker(unit, compiler) {
            this.compiler = compiler
            this.unit = unit
        }

        void checkHQL() {
            for (type in unit.types) {
                if (isCheckable(type.binding, unit)) {
                    whitelist = getWhitelist(type.binding, unit, compiler)
                    getAnnotationsInType(type)
                        .flatten()
                        .each { annotation ->
                        switch (qualifiedTypeName(annotation.resolvedType)) {
                            case jpa("NamedQuery"):
                                annotation.memberValuePairs.each { pair ->
                                    if (simpleVariableName(pair) == "query") {
                                        validateArgument(pair.value, false)
                                    }
                                }
                                break
                            case jpa("NamedQueries"):
                                annotation.memberValue.expressions.each { ann ->
                                    ann.memberValuePairs.each { pair ->
                                        if (simpleVariableName(pair) == "query") {
                                            validateArgument(pair.value, false)
                                        }
                                    }
                                }
                                break
                            case SPRING_QUERY_ANNOTATION:
                                if (annotation instanceof SingleMemberAnnotation) {
                                    if (annotation.memberValue instanceof StringLiteral) {
                                        validateArgument(annotation.memberValue, false)
                                    }
                                }
                                else if (annotation instanceof NormalAnnotation) {
                                    if (!ECJProcessor.isNativeQuery(annotation)) {
                                        Expression memberValue = ECJProcessor.getMemberValueOfAnnotation(annotation)
                                        if (memberValue instanceof StringLiteral) {
                                            validateArgument(memberValue, false)
                                        }
                                    }
                                }
                                break
                        }
                    }
                    type.methods.each { method ->
                        validateStatements(method.statements)
                    }
                }
            }
        }

        /**
         * @return a list of annotations both on the type and on the method. Returns empty list if there is no annotation.
         */
        private static Collection<?> getAnnotationsInType(def type) {
            def result = []
            if (type.annotations != null) {
                result += type.annotations
            }
            // We need method level annotations to support Spring's @Query
            if (type.methods != null && type.methods.annotations) {
                result += type.methods.annotations
            }
            return result - null
        }

        private void validateStatements(statements) {
            statements.each { statement -> validateStatement(statement) }
        }

        private void validateStatement(statement) {
            if (statement != null) switch (statement.class.simpleName) {
                case "MessageSend":
                    boolean ic = immediatelyCalled
                    switch (simpleMethodName(statement)) {
                        case "getResultList":
                        case "getSingleResult":
                            immediatelyCalled = true
                            break
                        case "createQuery":
                            statement.arguments.each { arg ->
                                if (arg.class.simpleName == "StringLiteral") {
                                    validateArgument(arg, true)
                                }
                            }
                            break
                        case "setParameter":
                            def arg = statement.arguments.first()
                            switch (arg.class.simpleName) {
                                case "IntLiteral":
                                    setParameterLabels.add(parseInt(new String((char[])arg.source())))
                                    break
                                case "StringLiteral":
                                    setParameterNames.add(new String((char[])arg.source()))
                                    break
                            }
                            break
                    }
                    validateStatement(statement.receiver)
                    setParameterLabels.clear()
                    setParameterNames.clear()
                    immediatelyCalled = ic
                    validateStatements(statement.arguments)
                    break
                case "AbstractVariableDeclaration":
                    validateStatement(statement.initialization)
                    break
                case "AssertStatement":
                    validateStatement(statement.assertExpression)
                    break
                case "Block":
                    validateStatements(statement.statements)
                    break
                case "SwitchStatement":
                    validateStatement(statement.expression)
                    validateStatements(statement.statements)
                    break
                case "ForStatement":
                    validateStatement(statement.action)
                    break
                case "ForeachStatement":
                    validateStatement(statement.collection)
                    validateStatement(statement.action)
                    break
                case "DoStatement":
                case "WhileStatement":
                    validateStatement(statement.condition)
                    validateStatement(statement.action)
                    break
                case "IfStatement":
                    validateStatement(statement.condition)
                    validateStatement(statement.thenStatement)
                    validateStatement(statement.elseStatement)
                    break
                case "TryStatement":
                    validateStatement(statement.tryBlock)
                    validateStatements(statement.catchBlocks)
                    validateStatement(statement.finallyBlock)
                    break
                case "SynchronizedStatement":
                    validateStatement(statement.expression)
                    validateStatement(statement.block)
                    break
                case "BinaryExpression":
                    validateStatement(statement.left)
                    validateStatement(statement.right)
                    break
                case "UnaryExpression":
                case "CastExpression":
                case "InstanceOfExpression":
                    validateStatement(statement.expression)
                    break
                case "ConditionalExpression":
                    validateStatement(statement.condition)
                    validateStatement(statement.valueIfTrue)
                    validateStatement(statement.valueIfFalse)
                    break
                case "LambdaExpression":
                    validateStatement(statement.body)
                    break
                case "ArrayInitializer":
                    validateStatements(statement.expressions)
                    break
                case "ArrayAllocationExpression":
                    validateStatements(statement.initializer)
                    break
                case "Assignment":
                    validateStatement(statement.lhs)
                    validateStatement(statement.expression)
                    break
                case "AllocationExpression":
                    validateStatements(statement.arguments)
                    break
                case "ReturnStatement":
                    validateStatement(statement.expression)
                    break
                case "ThrowStatement":
                    validateStatement(statement.exception)
                    break
                case "LabeledStatement":
                    validateStatement(statement.statement)
                    break
            }
        }

        void validateArgument(arg, boolean inCreateQueryMethod) {
            String hql = new String((char[]) arg.source())
            ErrorReporter handler = new ErrorReporter(arg, unit, compiler)
            validate(hql, inCreateQueryMethod && immediatelyCalled,
                    setParameterLabels, setParameterNames, handler,
                    new EclipseSessionFactory(whitelist, handler, unit))
        }

    }

    static class ErrorReporter implements Validation.Handler {

        private def node
        private def unit
        private def compiler

        ErrorReporter(node, unit, compiler) {
            this.compiler = compiler
            this.node = node
            this.unit = unit
        }

        @Override
        int getErrorCount() {
            return 0
        }

        @Override
        void throwQueryException() throws QueryException {}

        @Override
        void error(int start, int end, String message) {
            report(1, message, start, end)
        }

        @Override
        void warn(int start, int end, String message) {
            report(0, message, start, end)
        }

        private void report(int severity, String message, int offset, int endOffset) {
            def result = unit.compilationResult()
            char[] fileName = result.fileName
            int[] lineEnds = result.getLineSeparatorPositions()
            int startPosition
            int endPosition
            if (node!=null) {
                startPosition = node.sourceStart + offset
                endPosition = endOffset < 0 ?
                        node.sourceEnd - 1 :
                        node.sourceStart + endOffset
            }
            else {
                startPosition = 0
                endPosition = 0;
            }
            int lineNumber = startPosition >= 0 ?
                    getLineNumber(startPosition, lineEnds, 0, lineEnds.length - 1) : 0
            int columnNumber = startPosition >= 0 ?
                    searchColumnNumber(lineEnds, lineNumber, startPosition) : 0
            String[] args = [message]
            def problem =
                    compiler.problemReporter.problemFactory.createProblem(
                            fileName, 0,
                            args, args, severity,
                            startPosition, endPosition,
                            lineNumber, columnNumber)
            compiler.problemReporter.record(problem, result, unit, true)
        }

        @Override
        void reportError(RecognitionException e) {
            report(1, e.getMessage(), e.getColumn(), -1)
        }

        @Override
        void reportError(String text) {
            report(1, text, 1, -1)
        }

        @Override
        void reportWarning(String text) {
            report(0, text, 1, -1)
        }

        static int getLineNumber(int position, int[] lineEnds, int g, int d) {
            if (lineEnds == null)
                return 1
            if (d == -1)
                return 1
            int m = g, start
            while (g <= d) {
                m = g + (d - g) / 2
                if (position < (start = lineEnds[m])) {
                    d = m - 1
                } else if (position > start) {
                    g = m + 1
                } else {
                    return m + 1
                }
            }
            if (position < lineEnds[m]) {
                return m + 1
            }
            return m + 2
        }

        static int searchColumnNumber(int[] startLineIndexes, int lineNumber, int position) {
            switch (lineNumber) {
                case 1:
                    return position + 1
                case 2:
                    return position - startLineIndexes[0]
                default:
                    int line = lineNumber - 2
                    int length = startLineIndexes.length
                    if (line >= length) {
                        return position - startLineIndexes[length - 1]
                    }
                    return position - startLineIndexes[line]
            }
        }

    }

}
