package org.hibernate.query.validator

import antlr.RecognitionException
import org.hibernate.QueryException

import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.TypeElement

import static java.lang.Integer.parseInt
import static java.util.Collections.emptyList
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
            }
            else if (value.class.simpleName == "StringConstant") {
                names.add(value.stringValue())
            }
            else if (value.class.simpleName == "BinaryTypeBinding") {
                String name = qualifiedTypeName(value)
                def dialect
                try {
                    dialect = Class.forName(name).newInstance()
                    dialect.getFunctions()
                }
                catch (Exception e) {
                    try {
                        dialect = Class.forName(shadow(name)).newInstance()
                        dialect.getFunctions()
                    }
                    catch (Exception e2) {
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
        Set<String> setOrderBy = new HashSet<>();
        boolean immediatelyCalled = false

        private def unit
        private def compiler
        private List<String> whitelist
        private def processingEnv;

        Checker(unit, compiler, processingEnv) {
            this.compiler = compiler
            this.unit = unit
            this.processingEnv = processingEnv;
        }

        void checkHQL() {
            for (type in unit.types) {
                if (isCheckable(type.binding, unit)) {
                    whitelist = getWhitelist(type.binding, unit, compiler)
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
                    def elements = processingEnv.getElementUtils();
                    def typeElement = elements.getTypeElement(qualifiedName(type.binding));
                    def panacheEntity = PanacheUtils.isPanache(typeElement, processingEnv.getTypeUtils(), elements);
                    type.methods.each { method ->
                        validateStatements(type, panacheEntity, method.statements)
                    }
                }
            }
        }

        private String qualifiedName(type) {
            String pkgName = charToString(type.qualifiedPackageName());
            String className = charToString(type.qualifiedSourceName());
            return pkgName.isEmpty() ? className : pkgName + "."  + className;
        }

        private void validateStatements(type, panacheEntity, statements) {
            statements.each { statement -> validateStatement(type, panacheEntity, statement) }
        }

        private void validateStatement(type, panacheEntity, statement) {
            if (statement != null) switch (statement.class.simpleName) {
                case "MessageSend":
                    boolean ic = immediatelyCalled
                    def name = simpleMethodName(statement)
                    switch (name) {
                        case "getResultList":
                        case "getSingleResult":
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
//                            if (statement.receiver.class.simpleName == "SingleNameReference") {
//                                def ref = statement.receiver;
//                                String target = charToString(ref.token);
//                                def queryArg = firstArgument(statement);
//                                if (queryArg != null) {
//                                    checkPanacheQuery(queryArg, target, name, charToString(queryArg.source()), statement.arguments);
//                                }
                            if (statement.receiver.class.simpleName == "ThisReference" && panacheEntity != null) {
                                String target = panacheEntity.getSimpleName().toString();
                                def queryArg = firstArgument(statement);
                                if (queryArg != null) {
                                    checkPanacheQuery(queryArg, target, name, charToString(queryArg.source()), statement.arguments);
                                }
                            }
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

        def firstArgument(messageSend) {
            for (argument in messageSend.arguments) {
                if (argument.class.simpleName == "StringLiteral") {
                    return argument;
                }
            }
            return null;
        }

        void validateArgument(arg, boolean inCreateQueryMethod) {
            String hql = new String((char[]) arg.source())
            ErrorReporter handler = new ErrorReporter(arg, unit, compiler)
            validate(hql, inCreateQueryMethod && immediatelyCalled,
                    setParameterLabels, setParameterNames, handler,
                    sessionFactory.make(whitelist, handler, unit))
        }

        void checkPanacheQuery(stringLiteral, targetType, methodName, panacheQl, args) {
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

        String charToString(char[] charArray) {
            if (charArray == null) return null;
            return new String(charArray);
        }

        void collectPanacheArguments(args) {
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
                    def firstArg = args[firstArgIndex];
                    isParametersCall(firstArg);
                    if (setParameterNames.isEmpty()) {
                        for (int i = 0 ; i < args.length - firstArgIndex ; i++) {
                            setParameterLabels.add(1 + i);
                        }
                    }
                }
            }
        }
        boolean isParametersCall(firstArg) {
            if (firstArg.class.simpleName == "MessageSend") {
                def invocation = firstArg;
                String fieldName = charToString(invocation.selector);
                if (fieldName.equals("and") && isParametersCall(invocation.receiver)) {
                    def queryArg = firstArgument(invocation);
                    if (queryArg != null) {
                        setParameterNames.add(charToString(queryArg.source()));
                        return true;
                    }
                }
                else if (fieldName.equals("with")
                        && invocation.receiver.class.simpleName == "SingleNameReference") {
                    def receiver = invocation.receiver;
                    String target = charToString(receiver.token);
                    if (target.equals("Parameters")) {
                        def queryArg = firstArgument(invocation);
                        if (queryArg != null) {
                            setParameterNames.add(charToString(queryArg.source()));
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        boolean isSortCall(firstArg) {
            if (firstArg.class.simpleName == "MessageSend") {
                def invocation = firstArg;
                String fieldName = charToString(invocation.selector);
                    if ((fieldName.equals("and")
                            || fieldName.equals("descending")
                            || fieldName.equals("ascending")
                            || fieldName.equals("direction"))
                            && isSortCall(invocation.receiver)) {
                        for (e in invocation.arguments) {
                            if (e.class.simpleName == "StringLiteral") {
                                setOrderBy.add(charToString(e.source()));
                            }
                        }
                        return true;
                    }
                    else if ((fieldName.equals("by")
                            || fieldName.equals("descending")
                            || fieldName.equals("ascending"))
                            && invocation.receiver.class.simpleName == "SingleNameReference") {
                        def receiver = invocation.receiver;
                        String target = charToString(receiver.token);
                        if (target.equals("Sort")) {
                            for (e in invocation.arguments) {
                                if (e.class.simpleName == "StringLiteral") {
                                    setOrderBy.add(charToString(e.source()));
                                }
                            }
                            return true;
                        }
                    }
            }
            return false;
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
