package org.hibernate.query.validator

import static org.hibernate.query.validator.EclipseSessionFactory.getAnnotation
import static org.hibernate.query.validator.HQLProcessor.CHECK_HQL

/**
 * @author Gavin King
 */
class EclipseChecker {

    Set<Integer> setParameterLabels = new HashSet<>()
    Set<String> setParameterNames = new HashSet<>()
    Set<String> setOrderBy = new HashSet<>()
    boolean immediatelyCalled = false

    private def unit
    private def compiler
//        private List<String> whitelist
    private def processingEnv

    EclipseChecker(unit, compiler, processingEnv) {
        this.compiler = compiler
        this.unit = unit
        this.processingEnv = processingEnv
    }

    void checkHQL() {
        for (type in unit.types) {
            if (isCheckable(type.binding, unit)) {
//                    whitelist = getWhitelist(type.binding, unit, compiler)
                type.annotations.each { annotation ->
                    switch (EclipseSessionFactory.qualifiedTypeName(annotation.resolvedType)) {
                        case HQLProcessor.hibernate("processing.HQL"):
                            annotation.memberValuePairs.each { pair ->
                                if (EclipseSessionFactory.simpleVariableName(pair) == "value") {
                                    validateArgument(pair.value, false)
                                }
                            }
                            break
                        case HQLProcessor.jpa("NamedQuery"):
                        case HQLProcessor.hibernate("NamedQuery"):
                            annotation.memberValuePairs.each { pair ->
                                if (EclipseSessionFactory.simpleVariableName(pair) == "query") {
                                    validateArgument(pair.value, false)
                                }
                            }
                            break
                        case HQLProcessor.jpa("NamedQueries"):
                            annotation.memberValue.expressions.each { ann ->
                                ann.memberValuePairs.each { pair ->
                                    if (EclipseSessionFactory.simpleVariableName(pair) == "query") {
                                        validateArgument(pair.value, false)
                                    }
                                }
                            }
                            break
                    }
                }
                def elements = processingEnv.getElementUtils()
                def typeElement = elements.getTypeElement(qualifiedName(type.binding))
                def panacheEntity =
                        PanacheUtils.isPanache(typeElement, processingEnv.getTypeUtils(), elements)
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
                def name = EclipseSessionFactory.simpleMethodName(statement)
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
                                setParameterLabels.add(Integer.parseInt(new String((char[])arg.source())))
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
        EclipseErrorReporter handler = new EclipseErrorReporter(arg, unit, compiler, hql)
        Validation.validate(hql, inCreateQueryMethod && immediatelyCalled,
                setParameterLabels, setParameterNames, handler,
                EclipseProcessor.sessionFactory.make(unit))
    }

    void checkPanacheQuery(stringLiteral, targetType, methodName, panacheQl, args) {
        EclipseErrorReporter handler = new EclipseErrorReporter(stringLiteral, unit, compiler, panacheQl)
        collectPanacheArguments(args)
        int[] offset = new int[1]
        String hql = PanacheUtils.panacheQlToHql(handler, targetType, methodName,
                                                 panacheQl, offset, setParameterLabels, setOrderBy)
        if (hql != null) {
            Validation.validate(hql, true,
                     setParameterLabels, setParameterNames, handler,
                    EclipseProcessor.sessionFactory.make(unit), offset[0])
        }
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
            if (isPanacheSortCall(args[firstArgIndex])) {
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

    boolean isPanacheSortCall(firstArg) {
        if (firstArg.getClass().simpleName == "MessageSend") {
            def invocation = firstArg
            String fieldName = charToString(invocation.selector)
                if ((fieldName.equals("and")
                        || fieldName.equals("descending")
                        || fieldName.equals("ascending")
                        || fieldName.equals("direction"))
                        && isPanacheSortCall(invocation.receiver)) {
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

    private static boolean isCheckable(type, unit) {
        return getCheckAnnotation(type, unit)!=null
    }

    private static def getCheckAnnotation(type, unit) {
        def result = getAnnotation(type, CHECK_HQL)
        if (result!=null) return result
        def packInfo = unit.scope.getType("package-info".toCharArray())
        return getAnnotation(packInfo, CHECK_HQL)
    }
}
