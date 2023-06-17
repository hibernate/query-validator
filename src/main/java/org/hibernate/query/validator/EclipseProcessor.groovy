//file:noinspection GroovyFallthrough
package org.hibernate.query.validator

import org.antlr.v4.runtime.LexerNoViableAltException
import org.antlr.v4.runtime.Parser
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.atn.ATNConfigSet
import org.antlr.v4.runtime.dfa.DFA
import org.eclipse.jdt.internal.compiler.problem.ProblemSeverities

import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.TypeElement
import javax.tools.Diagnostic

import static java.lang.Integer.parseInt
import static org.hibernate.query.hql.internal.StandardHqlTranslator.prettifyAntlrError
import static org.hibernate.query.validator.EclipseSessionFactory.*
import static org.hibernate.query.validator.HQLProcessor.CHECK_HQL
import static org.hibernate.query.validator.HQLProcessor.jpa
import static org.hibernate.query.validator.HQLProcessor.hibernate
import static org.hibernate.query.validator.Validation.validate

/**
 * Annotation processor that validates HQL and JPQL queries
 * for Eclipse.
 *
 * @see org.hibernate.annotations.processing.CheckHQL
 *
 * @author Gavin King
 */
//@SupportedAnnotationTypes(CHECK_HQL)
class EclipseProcessor extends AbstractProcessor {

    private ProcessingEnvironment processingEnv

    synchronized void init(ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv
        super.init(processingEnv)
        processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "Hibernate Query Validator for Eclipse");
    }

    static Mocker<EclipseSessionFactory> sessionFactory = Mocker.variadic(EclipseSessionFactory.class)

    @Override
    boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        def compiler = processingEnv.getCompiler()
        if (!roundEnv.getRootElements().isEmpty()) {
            for (unit in compiler.unitsToProcess) {
                compiler.parser.getMethodBodies(unit)
                new Checker(unit, compiler, processingEnv).checkHQL()
            }
        }
        return false
    }

    private static boolean isCheckable(type, unit) {
        return getCheckAnnotation(type, unit)!=null
    }

//    private final static String ORG_HIBERNATE =
//            new StringBuilder("org.")
//                    .append("hibernate.")
//                    .toString()

//    private static String shadow(String name) {
//        return name.replace(ORG_HIBERNATE + "dialect",
//                ORG_HIBERNATE + "query.validator.hibernate.dialect")
//    }

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
        Set<String> setOrderBy = new HashSet<>()
        boolean immediatelyCalled = false

        private def unit
        private def compiler
//        private List<String> whitelist
        private def processingEnv

        Checker(unit, compiler, processingEnv) {
            this.compiler = compiler
            this.unit = unit
            this.processingEnv = processingEnv
        }

        void checkHQL() {
            for (type in unit.types) {
                if (isCheckable(type.binding, unit)) {
//                    whitelist = getWhitelist(type.binding, unit, compiler)
                    type.annotations.each { annotation ->
                        switch (qualifiedTypeName(annotation.resolvedType)) {
                            case hibernate("processing.HQL"):
                                annotation.memberValuePairs.each { pair ->
                                    if (simpleVariableName(pair) == "value") {
                                        validateArgument(pair.value, false)
                                    }
                                }
                                break
                            case jpa("NamedQuery"):
                            case hibernate("NamedQuery"):
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
                    def elements = processingEnv.getElementUtils()
                    def typeElement = elements.getTypeElement(qualifiedName(type.binding))
                    def panacheEntity = PanacheUtils.isPanache(typeElement, processingEnv.getTypeUtils(), elements)
                    type.methods.each { method ->
                        validateStatements(type, panacheEntity, method.statements)
                    }
                }
            }
        }

        private static String qualifiedName(type) {
            String pkgName = charToString(type.qualifiedPackageName())
            String className = charToString(type.qualifiedSourceName())
            return pkgName.isEmpty() ? className : pkgName + "."  + className
        }

        private void validateStatements(type, panacheEntity, statements) {
            statements.each { statement -> validateStatement(type, panacheEntity, statement) }
        }

        private void validateStatement(type, panacheEntity, statement) {
            if (statement != null) switch (statement.getClass().simpleName) {
                case "MessageSend":
                    boolean ic = immediatelyCalled
                    def name = simpleMethodName(statement)
                    switch (name) {
                        case "getResultList":
                        case "getSingleResult":
                        case "getSingleResultOrNull":
                            immediatelyCalled = true
                            break
                        case "count":
                        case "delete":
                        case "update":
                        case "exists":
                        case "stream":
                        case "list":
                        case "find":
                        // Disabled until we find how to support this type-safe in Javac
//                            if (statement.receiver.getClass().simpleName == "SingleNameReference") {
//                                def ref = statement.receiver;
//                                String target = charToString(ref.token);
//                                def queryArg = firstArgument(statement);
//                                if (queryArg != null) {
//                                    checkPanacheQuery(queryArg, target, name, charToString(queryArg.source()), statement.arguments)
//                                }
                            if (statement.receiver.getClass().simpleName == "ThisReference" && panacheEntity != null) {
                                String target = panacheEntity.getSimpleName().toString()
                                def queryArg = firstArgument(statement)
                                if (queryArg != null) {
                                    checkPanacheQuery(queryArg, target, name, charToString(queryArg.source()), statement.arguments)
                                }
                            }
                            break
                        case "createQuery":
                        case "createSelectionQuery":
                        case "createMutationQuery":
                            statement.arguments.each { arg ->
                                if (arg.getClass().simpleName == "StringLiteral"
                                        || arg.getClass().simpleName == "ExtendedStringLiteral") {
                                    validateArgument(arg, true)
                                }
                            }
                            break
                        case "setParameter":
                            def arg = statement.arguments.first()
                            switch (arg.getClass().simpleName) {
                                case "IntLiteral":
                                    setParameterLabels.add(parseInt(new String((char[])arg.source())))
                                    break
                                case "StringLiteral":
                                case "ExtendedStringLiteral":
                                    setParameterNames.add(new String((char[])arg.source()))
                                    break
                            }
                            break
                    }
                    validateStatement(type, panacheEntity, statement.receiver)
                    setParameterLabels.clear()
                    setParameterNames.clear()
                    immediatelyCalled = ic
                    validateStatements(type, panacheEntity, statement.arguments)
                    break
                case "AbstractVariableDeclaration":
                    validateStatement(type, panacheEntity, statement.initialization)
                    break
                case "AssertStatement":
                    validateStatement(type, panacheEntity, statement.assertExpression)
                    break
                case "Block":
                    validateStatements(type, panacheEntity, statement.statements)
                    break
                case "SwitchStatement":
                    validateStatement(type, panacheEntity, statement.expression)
                    validateStatements(type, panacheEntity, statement.statements)
                    break
                case "ForStatement":
                    validateStatement(type, panacheEntity, statement.action)
                    break
                case "ForeachStatement":
                    validateStatement(type, panacheEntity, statement.collection)
                    validateStatement(type, panacheEntity, statement.action)
                    break
                case "DoStatement":
                case "WhileStatement":
                    validateStatement(type, panacheEntity, statement.condition)
                    validateStatement(type, panacheEntity, statement.action)
                    break
                case "IfStatement":
                    validateStatement(type, panacheEntity, statement.condition)
                    validateStatement(type, panacheEntity, statement.thenStatement)
                    validateStatement(type, panacheEntity, statement.elseStatement)
                    break
                case "TryStatement":
                    validateStatement(type, panacheEntity, statement.tryBlock)
                    validateStatements(type, panacheEntity, statement.catchBlocks)
                    validateStatement(type, panacheEntity, statement.finallyBlock)
                    break
                case "SynchronizedStatement":
                    validateStatement(type, panacheEntity, statement.expression)
                    validateStatement(type, panacheEntity, statement.block)
                    break
                case "BinaryExpression":
                    validateStatement(type, panacheEntity, statement.left)
                    validateStatement(type, panacheEntity, statement.right)
                    break
                case "UnaryExpression":
                case "CastExpression":
                case "InstanceOfExpression":
                    validateStatement(type, panacheEntity, statement.expression)
                    break
                case "ConditionalExpression":
                    validateStatement(type, panacheEntity, statement.condition)
                    validateStatement(type, panacheEntity, statement.valueIfTrue)
                    validateStatement(type, panacheEntity, statement.valueIfFalse)
                    break
                case "LambdaExpression":
                    validateStatement(type, panacheEntity, statement.body)
                    break
                case "ArrayInitializer":
                    validateStatements(type, panacheEntity, statement.expressions)
                    break
                case "ArrayAllocationExpression":
                    validateStatements(type, panacheEntity, statement.initializer)
                    break
                case "Assignment":
                    validateStatement(type, panacheEntity, statement.lhs)
                    validateStatement(type, panacheEntity, statement.expression)
                    break
                case "AllocationExpression":
                    validateStatements(type, panacheEntity, statement.arguments)
                    break
                case "ReturnStatement":
                    validateStatement(type, panacheEntity, statement.expression)
                    break
                case "ThrowStatement":
                    validateStatement(type, panacheEntity, statement.exception)
                    break
                case "LabeledStatement":
                    validateStatement(type, panacheEntity, statement.statement)
                    break
            }
        }

        static def firstArgument(messageSend) {
            for (argument in messageSend.arguments) {
                if (argument.getClass().simpleName == "StringLiteral" ||
                        argument.getClass().simpleName == "ExtendedStringLiteral") {
                    return argument
                }
            }
            return null
        }

        void validateArgument(arg, boolean inCreateQueryMethod) {
            String hql = new String((char[]) arg.source())
            ErrorReporter handler = new ErrorReporter(arg, unit, compiler, hql)
            validate(hql, inCreateQueryMethod && immediatelyCalled,
                    setParameterLabels, setParameterNames, handler,
                    sessionFactory.make(unit))
        }

        void checkPanacheQuery(stringLiteral, targetType, methodName, panacheQl, args) {
            ErrorReporter handler = new ErrorReporter(stringLiteral, unit, compiler, panacheQl)
            collectPanacheArguments(args)
            int[] offset = new int[1]
            String hql = PanacheUtils.panacheQlToHql(handler, targetType, methodName, 
                                                     panacheQl, offset, setParameterLabels, setOrderBy)
            if (hql == null)
                return
            validate(hql, true,
                     setParameterLabels, setParameterNames, handler,
                     sessionFactory.make(unit), offset[0])
        }

        static String charToString(char[] charArray) {
            if (charArray == null) return null
            return new String(charArray)
        }

        void collectPanacheArguments(args) {
            // first arg is pql
            // second arg can be Sort, Object..., Map or Parameters
            setParameterLabels.clear()
            setParameterNames.clear()
            setOrderBy.clear()
            if (args.length > 1) {
                int firstArgIndex = 1
                if (isSortCall(args[firstArgIndex])) {
                    firstArgIndex++
                }
                
                if (args.length > firstArgIndex) {
                    def firstArg = args[firstArgIndex]
                    isParametersCall(firstArg)
                    if (setParameterNames.isEmpty()) {
                        for (int i = 0 ; i < args.length - firstArgIndex ; i++) {
                            setParameterLabels.add(1 + i)
                        }
                    }
                }
            }
        }
        boolean isParametersCall(firstArg) {
            if (firstArg.getClass().simpleName == "MessageSend") {
                def invocation = firstArg
                String fieldName = charToString(invocation.selector)
                if (fieldName.equals("and") && isParametersCall(invocation.receiver)) {
                    def queryArg = firstArgument(invocation)
                    if (queryArg != null) {
                        setParameterNames.add(charToString(queryArg.source()))
                        return true
                    }
                }
                else if (fieldName.equals("with")
                        && invocation.receiver.getClass().simpleName == "SingleNameReference") {
                    def receiver = invocation.receiver
                    String target = charToString(receiver.token)
                    if (target.equals("Parameters")) {
                        def queryArg = firstArgument(invocation)
                        if (queryArg != null) {
                            setParameterNames.add(charToString(queryArg.source()))
                            return true
                        }
                    }
                }
            }
            return false
        }

        boolean isSortCall(firstArg) {
            if (firstArg.getClass().simpleName == "MessageSend") {
                def invocation = firstArg
                String fieldName = charToString(invocation.selector)
                    if ((fieldName.equals("and")
                            || fieldName.equals("descending")
                            || fieldName.equals("ascending")
                            || fieldName.equals("direction"))
                            && isSortCall(invocation.receiver)) {
                        for (e in invocation.arguments) {
                            if (e.getClass().simpleName == "StringLiteral") {
                                setOrderBy.add(charToString(e.source()))
                            }
                        }
                        return true
                    }
                    else if ((fieldName.equals("by")
                            || fieldName.equals("descending")
                            || fieldName.equals("ascending"))
                            && invocation.receiver.getClass().simpleName == "SingleNameReference") {
                        def receiver = invocation.receiver
                        String target = charToString(receiver.token)
                        if (target.equals("Sort")) {
                            for (e in invocation.arguments) {
                                if (e.getClass().simpleName == "StringLiteral") {
                                    setOrderBy.add(charToString(e.source()))
                                }
                            }
                            return true
                        }
                    }
            }
            return false
        }

    }

    static class ErrorReporter implements Validation.Handler {

        private def node
        private def unit
        private def compiler
        private int errorcount
        private final String hql

        ErrorReporter(node, unit, compiler, String hql) {
            this.hql = hql
            this.compiler = compiler
            this.node = node
            this.unit = unit
        }

        @Override
        int getErrorCount() {
            return errorcount
        }

        @Override
        void syntaxError(Recognizer<?, ?> recognizer, Object symbol, int line, int charInLine, String message, RecognitionException e) {
            message = prettifyAntlrError(symbol, line, charInLine, message, e, hql, false)
            errorcount++
            def result = unit.compilationResult()
            char[] fileName = result.fileName
            int[] lineEnds = result.getLineSeparatorPositions()
            int startIndex
            int stopIndex
            int lineNum = getLineNumber(node.sourceStart, lineEnds, 0, lineEnds.length - 1)
            Token offendingToken = e.getOffendingToken()
            if ( offendingToken != null ) {
                startIndex = offendingToken.getStartIndex()
                stopIndex = offendingToken.getStopIndex()
            }
            else if ( e instanceof LexerNoViableAltException ) {
                startIndex = ((LexerNoViableAltException) e).getStartIndex()
                stopIndex = startIndex;
            }
            else {
                startIndex = lineEnds[line-1] + charInLine
                stopIndex = startIndex
            }
            int startPosition = node.sourceStart + startIndex + 1
            int endPosition = node.sourceStart + stopIndex + 1
            if ( endPosition < startPosition ) {
                endPosition = startPosition
            }
            def problem =
                    compiler.problemReporter.problemFactory
                            .createProblem(fileName, 0,
                                    new String[]{message},
                                    new String[]{message},
                                    ProblemSeverities.Error,
                                    startPosition,
                                    endPosition,
                                    lineNum+line-1, -1)
            compiler.problemReporter.record(problem, result, unit, true)
        }

        @Override
        void reportAmbiguity(Parser parser, DFA dfa, int i, int i1, boolean b, BitSet bitSet, ATNConfigSet atnConfigSet) {
        }

        @Override
        void reportAttemptingFullContext(Parser parser, DFA dfa, int i, int i1, BitSet bitSet, ATNConfigSet atnConfigSet) {
        }

        @Override
        void reportContextSensitivity(Parser parser, DFA dfa, int i, int i1, int i2, ATNConfigSet atnConfigSet) {
        }

        @Override
        void error(int start, int end, String message) {
            report(1, message, start, end)
        }

        @Override
        void warn(int start, int end, String message) {
            report(0, message, start, end)
        }

        private void report(int severity, String message, int offset, int endOffset) {
            errorcount++
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
                endPosition = 0
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
                }
                else if (position > start) {
                    g = m + 1
                }
                else {
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
