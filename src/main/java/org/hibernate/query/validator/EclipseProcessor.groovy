package org.hibernate.query.validator

import antlr.RecognitionException
import org.hibernate.QueryException
import org.hibernate.hql.internal.ast.ParseErrorHandler

import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.TypeElement

import static java.lang.Integer.parseInt
import static org.hibernate.query.validator.EclipseSessionFactory.*
import static org.hibernate.query.validator.HQLProcessor.CHECK_HQL
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
        def packInfo = unit.scope.getType("package-info".toCharArray())
        return hasAnnotation(packInfo, CHECK_HQL) ||
                hasAnnotation(type, CHECK_HQL)
    }

    @Override
    SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported()
    }

    static class Checker {

        Set<Integer> setParameterLabels = new HashSet<>()
        Set<String> setParameterNames = new HashSet<>()
        boolean immediatelyCalled = false;

        private def unit
        private def compiler

        Checker(unit, compiler) {
            this.compiler = compiler
            this.unit = unit
        }

        private void checkHQL() {
            for (type in unit.types) {
                if (isCheckable(type.binding, unit)) {
                    type.annotations.each { annotation ->
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
                        }
                    }
                    type.methods.each { method ->
                        validateStatements(method.statements)
                    }
                }
            }
        }

        private void validateStatements(statements) {
            statements.each { statement -> validateStatement(statement) }
        }

        private void validateStatement(statement) {
            if (statement != null) switch (statement.class.simpleName) {
                case "MessageSend":
                    boolean ic = immediatelyCalled;
                    switch (simpleMethodName(statement)) {
                        case "getResultList":
                        case "getSingleResult":
                            immediatelyCalled = true;
                            break;
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
                    immediatelyCalled = ic;
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
                    new EclipseSessionFactory(handler, unit))
        }

    }

    static class ErrorReporter implements ParseErrorHandler {

        private def literal
        private def unit
        private def compiler

        ErrorReporter(literal, unit, compiler) {
            this.compiler = compiler
            this.literal = literal
            this.unit = unit
        }

        @Override
        int getErrorCount() {
            return 0
        }

        @Override
        void throwQueryException() throws QueryException {}

        private void report(int severity, String message, int offset) {
            def result = unit.compilationResult()
            char[] fileName = result.fileName
            int[] lineEnds = result.getLineSeparatorPositions()
            int startPosition = literal.sourceStart + offset + 1
            int endPosition = literal.sourceEnd - 1 //search for the end of the token!
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
            report(1, e.getMessage(), e.getColumn() - 1)
        }

        @Override
        void reportError(String text) {
            report(1, text, 0)
        }

        @Override
        void reportWarning(String text) {
            report(0, text, 0)
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
