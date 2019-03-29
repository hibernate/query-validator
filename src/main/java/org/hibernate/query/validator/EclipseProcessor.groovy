package org.hibernate.query.validator

import antlr.RecognitionException
import org.hibernate.QueryException
import org.hibernate.hql.internal.ast.ParseErrorHandler

import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.TypeElement

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
                checkHQL(unit, compiler)
            }
        }
        return false
    }

    private static boolean isCheckable(type, unit) {
        def packInfo = unit.scope.getType("package-info".toCharArray())
        return hasAnnotation(packInfo, CHECK_HQL) ||
                hasAnnotation(type, CHECK_HQL)
    }

    private void checkHQL(unit, compiler) {
        for (type in unit.types) {
            if (isCheckable(type.binding, unit)) {
                type.annotations.each { annotation ->
                    switch (qualifiedTypeName(annotation.resolvedType)) {
                        case jpa("NamedQuery"):
                            annotation.memberValuePairs.each { pair ->
                                if (simpleVariableName(pair) == "query") {
                                    validateArgument(pair.value, unit, compiler)
                                }
                            }
                            break
                        case jpa("NamedQueries"):
                            annotation.memberValue.expressions.each { ann ->
                                ann.memberValuePairs.each { pair ->
                                    if (simpleVariableName(pair) == "query") {
                                        validateArgument(pair.value, unit, compiler)
                                    }
                                }
                            }
                            break
                    }
                }
                type.methods.each { method ->
                    validateStatements(method.statements, unit, compiler)
                }
            }
        }
    }

    private void validateStatements(statements, unit, compiler) {
        statements.each { statement -> validateStatement(statement, unit, compiler) }
    }

    private void validateStatement(statement, unit, compiler) {
        if (statement != null) switch (statement.class.simpleName) {
            case "MessageSend":
                if (simpleMethodName(statement) == "createQuery") {
                    statement.arguments.each { arg ->
                        if (arg.class.simpleName == "StringLiteral") {
                            validateArgument(arg, unit, compiler)
                        }
                    }
                }
                validateStatement(statement.receiver, unit, compiler)
                validateStatements(statement.arguments, unit, compiler)
                break
            case "AbstractVariableDeclaration":
                validateStatement(statement.initialization, unit, compiler)
                break
            case "AssertStatement":
                validateStatement(statement.assertExpression, unit, compiler)
                break
            case "Block":
                validateStatements(statement.statements, unit, compiler)
                break
            case "SwitchStatement":
                validateStatement(statement.expression, unit, compiler)
                validateStatements(statement.statements, unit, compiler)
                break
            case "ForStatement":
                validateStatement(statement.action, unit, compiler)
                break
            case "ForeachStatement":
                validateStatement(statement.collection, unit, compiler)
                validateStatement(statement.action, unit, compiler)
                break
            case "DoStatement":
            case "WhileStatement":
                validateStatement(statement.condition, unit, compiler)
                validateStatement(statement.action, unit, compiler)
                break
            case "IfStatement":
                validateStatement(statement.condition, unit, compiler)
                validateStatement(statement.thenStatement, unit, compiler)
                validateStatement(statement.elseStatement, unit, compiler)
                break
            case "TryStatement":
                validateStatement(statement.tryBlock, unit, compiler)
                validateStatements(statement.catchBlocks, unit, compiler)
                validateStatement(statement.finallyBlock, unit, compiler)
                break
            case "SynchronizedStatement":
                validateStatement(statement.expression, unit, compiler)
                validateStatement(statement.block, unit, compiler)
                break
            case "BinaryExpression":
                validateStatement(statement.left, unit, compiler)
                validateStatement(statement.right, unit, compiler)
                break
            case "UnaryExpression":
            case "CastExpression":
            case "InstanceOfExpression":
                validateStatement(statement.expression, unit, compiler)
                break
            case "ConditionalExpression":
                validateStatement(statement.condition, unit, compiler)
                validateStatement(statement.valueIfTrue, unit, compiler)
                validateStatement(statement.valueIfFalse, unit, compiler)
                break
            case "LambdaExpression":
                validateStatement(statement.body, unit, compiler)
                break
            case "ArrayInitializer":
                validateStatements(statement.expressions, unit, compiler)
                break
            case "ArrayAllocationExpression":
                validateStatements(statement.initializer, unit, compiler)
                break
            case "Assignment":
                validateStatement(statement.lhs, unit, compiler)
                validateStatement(statement.expression, unit, compiler)
                break
            case "AllocationExpression":
                validateStatements(statement.arguments, unit, compiler)
                break
            case "ReturnStatement":
                validateStatement(statement.expression, unit, compiler)
                break
            case "ThrowStatement":
                validateStatement(statement.exception, unit, compiler)
                break
            case "LabeledStatement":
                validateStatement(statement.statement, unit, compiler)
                break
        }
    }

    void validateArgument(arg, unit, compiler) {
        String hql = new String((char[]) arg.source())
        ErrorReporter handler = new ErrorReporter(arg, unit, compiler)
        validate(hql, handler, new EclipseSessionFactory(handler, unit))
    }

    @Override
    SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported()
    }

    class ErrorReporter implements ParseErrorHandler {

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

        int getLineNumber(int position, int[] lineEnds, int g, int d) {
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

        int searchColumnNumber(int[] startLineIndexes, int lineNumber, int position) {
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
