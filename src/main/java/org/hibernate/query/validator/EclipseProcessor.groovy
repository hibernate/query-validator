package org.hibernate.query.validator

import antlr.RecognitionException
import org.hibernate.QueryException
import org.hibernate.hql.internal.ast.ParseErrorHandler

import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.PackageElement
import javax.lang.model.element.TypeElement

import static org.hibernate.query.validator.EclipseSessionFactory.*
import static org.hibernate.query.validator.Validation.validate

/**
 * Annotation processor that validates HQL and JPQL queries
 * for Eclipse.
 *
 * @see CheckHQL
 */
//@SupportedAnnotationTypes("org.hibernate.query.validator.CheckHQL")
//@AutoService(Processor.class)
class EclipseProcessor extends AbstractProcessor {

    @Override
    boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (TypeElement annotation in annotations) {
            for (Element element in roundEnv.getElementsAnnotatedWith(annotation)) {
                if (element instanceof PackageElement) {
                    for (Element root in roundEnv.getRootElements()) {
                        Element enclosing = root.getEnclosingElement()
                        if (enclosing != null && enclosing == element) {
                            checkHQL(root)
                        }
                    }
                } else {
                    checkHQL(element)
                }
            }
        }
        return false
    }

    @Override
    synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv)
        EclipseSessionFactory.initialize(processingEnv)
    }

    private void checkHQL(Element element) {
        def compiler = processingEnv.compiler
        compiler.unitsToProcess.each { unit ->
            compiler.parser.getMethodBodies(unit)
            unit.types.each { type ->
                if (qualifiedTypeName(type.binding) == element.toString()) {
                    type.methods.each { method ->
                        validateStatements(method.statements, unit)
                    }
                    type.annotations.each { annotation ->
                        switch (qualifiedTypeName(annotation.resolvedType)) {
                            case "javax.persistence.NamedQuery":
                                annotation.memberValuePairs.each { pair ->
                                    if (simpleVariableName(pair) == "query") {
                                        validateArgument(pair.value, unit)
                                    }
                                }
                                break
                            case "javax.persistence.NamedQueries":
                                annotation.memberValue.expressions.each { ann ->
                                    ann.memberValuePairs.each { pair ->
                                        if (simpleVariableName(pair) == "query") {
                                            validateArgument(pair.value, unit)
                                        }
                                    }
                                }
                                break
                        }
                    }
                }
            }
        }
    }

    private void validateStatements(statements, unit) {
        statements.each { statement -> validateStatement(statement, unit) }
    }

    private void validateStatement(statement, unit) {
        if (statement != null) switch (statement.class.simpleName) {
            case "MessageSend":
                if (simpleMethodName(statement) == "createQuery") {
                    statement.arguments.each { arg ->
                        if (arg.class.simpleName == "StringLiteral") {
                            validateArgument(arg, unit)
                        }
                    }
                }
                validateStatement(statement.receiver, unit)
                validateStatements(statement.arguments, unit)
                break
            case "AbstractVariableDeclaration":
                validateStatement(statement.initialization, unit)
                break;
            case "AssertStatement":
                validateStatement(statement.assertExpression, unit)
                break;
            case "Block":
                validateStatements(statement.statements, unit)
                break
            case "SwitchStatement":
                validateStatement(statement.expression, unit)
                validateStatements(statement.statements, unit)
                break
            case "ForStatement":
                validateStatement(statement.action, unit)
                break;
            case "ForeachStatement":
                validateStatement(statement.collection, unit)
                validateStatement(statement.action, unit)
                break;
            case "DoStatement":
            case "WhileStatement":
                validateStatement(statement.condition, unit)
                validateStatement(statement.action, unit)
                break
            case "IfStatement":
                validateStatement(statement.condition, unit)
                validateStatement(statement.thenStatement, unit)
                validateStatement(statement.elseStatement, unit)
                break
            case "TryStatement":
                validateStatement(statement.tryBlock, unit)
                validateStatements(statement.catchBlocks, unit)
                validateStatement(statement.finallyBlock, unit)
                break
            case "SynchronizedStatement":
                validateStatement(statement.expression, unit)
                validateStatement(statement.block, unit)
                break
            case "BinaryExpression":
                validateStatement(statement.left, unit)
                validateStatement(statement.right, unit)
                break
            case "UnaryExpression":
            case "CastExpression":
            case "InstanceOfExpression":
                validateStatement(statement.expression, unit)
                break
            case "ConditionalExpression":
                validateStatement(statement.condition, unit)
                validateStatement(statement.valueIfTrue, unit)
                validateStatement(statement.valueIfFalse, unit)
                break
            case "LambdaExpression":
                validateStatement(statement.body, unit)
                break
            case "ArrayInitializer":
                validateStatements(statement.expressions, unit)
                break
            case "ArrayAllocationExpression":
                validateStatements(statement.initializer, unit)
                break
            case "Assignment":
                validateStatement(statement.lhs, unit)
                validateStatement(statement.expression, unit)
                break
            case "AllocationExpression":
                validateStatements(statement.arguments, unit)
                break
            case "ReturnStatement":
                validateStatement(statement.expression, unit);
                break
            case "ThrowStatement":
                validateStatement(statement.exception, unit);
                break
            case "LabeledStatement":
                validateStatement(statement.statement, unit);
                break
        }
    }

    void validateArgument(arg, unit) {
        String hql = new String((char[]) arg.source())
        ErrorReporter handler = new ErrorReporter(arg, unit)
        validate(hql, handler, new EclipseSessionFactory(handler))
    }

    @Override
    SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported()
    }

    class ErrorReporter implements ParseErrorHandler {

        private def literal
        private def unit

        ErrorReporter(literal, unit) {
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
