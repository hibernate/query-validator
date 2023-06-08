package org.hibernate.query.validator

import org.eclipse.jdt.internal.compiler.lookup.Binding
import org.hibernate.PropertyNotFoundException
import org.hibernate.engine.spi.Mapping
import org.hibernate.type.*
import org.hibernate.type.descriptor.java.EnumJavaType
import org.hibernate.type.descriptor.jdbc.IntegerJdbcType
import org.hibernate.type.internal.BasicTypeImpl

import javax.persistence.AccessType
import java.beans.Introspector

import static java.util.Arrays.stream
import static org.hibernate.internal.util.StringHelper.*
import static org.hibernate.query.validator.HQLProcessor.jpa

/**
 * @author Gavin King
 */
abstract class EclipseSessionFactory extends MockSessionFactory {

    private static final Mocker<EntityPersister> entityPersister = Mocker.variadic(EntityPersister.class)
    private static final Mocker<ToManyAssociationPersister> toManyPersister = Mocker.variadic(ToManyAssociationPersister.class)
    private static final Mocker<ElementCollectionPersister> collectionPersister = Mocker.variadic(ElementCollectionPersister.class)
    private static final Mocker<Component> component = Mocker.variadic(Component.class)

    final def unit

    EclipseSessionFactory(unit) {
        this.unit = unit
    }

    @Override
    MockEntityPersister createMockEntityPersister(String entityName) {
        def type = findEntityClass(entityName)
        return type == null ? null :
                entityPersister.make(entityName, type, this, getDefaultAccessType(type))
    }

    @Override
    MockCollectionPersister createMockCollectionPersister(String role) {
        String entityName = root(role) //only works because entity names don't contain dots
        String propertyPath = unroot(role)
        def entityClass = findEntityClass(entityName)
        AccessType defaultAccessType = getDefaultAccessType(entityClass)
        def property =
                findPropertyByPath(entityClass, propertyPath, defaultAccessType)
        CollectionType collectionType = collectionType(getMemberType(property), role)
        if (isToManyAssociation(property)) {
            return toManyPersister.make(role, collectionType,
                    getToManyTargetEntityName(property), this)
        }
        else if (isElementCollectionProperty(property)) {
            def elementType =
                    getElementCollectionElementType(property)
            return collectionPersister.make(role, collectionType,
                    elementType, propertyPath, defaultAccessType,
                    elementCollectionElementType(elementType, role,
                            propertyPath, defaultAccessType),
                    this)
        }
        else {
            return null
        }
    }

    private static def findPropertyByPath(type,
                                          String propertyPath,
                                          AccessType defaultAccessType) {
        return split(".", propertyPath).inject(type) {
            current, segment ->
                current == null ? null :
                        findProperty(getMemberType(current),
                                segment, defaultAccessType)
        }
    }

    static Type propertyType(member,
                             String entityName, String path,
                             AccessType defaultAccessType) {
        def memberType = getMemberType(member)
        if (isEmbeddedProperty(member)) {
            return component.make(memberType, entityName, path, defaultAccessType);
        }
        else if (isToOneAssociation(member)) {
            String targetEntity = getToOneTargetEntity(member)
            return new ManyToOneType(typeConfiguration, targetEntity)
        }
        else if (isToManyAssociation(member)) {
            return collectionType(memberType, qualify(entityName, path))
        }
        else if (isElementCollectionProperty(member)) {
            return collectionType(memberType, qualify(entityName, path))
        }
        else if (isEnumProperty(member)) {
            return new BasicTypeImpl(new EnumJavaType(Object.class), IntegerJdbcType.INSTANCE);
        }
        else {
            Type result = typeConfiguration.getBasicTypeRegistry().getRegisteredType(qualifiedTypeName(memberType))
            return result;// == null ? unknownType : result
        }
    }

    private static Type elementCollectionElementType(elementType,
                                                     String role, String path,
                                                     AccessType defaultAccessType) {
        if (isEmbeddableType(elementType)) {
            return component.make(elementType, role, path, defaultAccessType);
        }
        else {
            return typeConfiguration.getBasicTypeRegistry().getRegisteredType(qualifiedTypeName(elementType))
        }
    }

    private static CollectionType collectionType(type, String role) {
        return createCollectionType(role, simpleTypeName(type.actualType()))
    }

    abstract static class Component implements CompositeType {
        private String[] propertyNames
        private Type[] propertyTypes
        def type

        Component(type, String entityName, String path,
                  AccessType defaultAccessType) {
            this.type = type

            List<String> names = []
            List<Type> types = []

            while (type != null && type.metaClass.hasProperty(type, "superclass")) {
                def classSymbol = type
                if (isMappedClass(type)) { //ignore unmapped intervening classes
                    AccessType accessType =
                            getAccessType(type, defaultAccessType)
                    for (member in classSymbol.methods()) {
                        if (isPersistable(member, accessType)) {
                            String name = propertyName(member)
                            Type propertyType =
                                    propertyType(member, entityName,
                                            qualify(path, name),
                                            defaultAccessType)
                            if (propertyType != null) {
                                names.add(name)
                                types.add(propertyType)
                            }
                        }
                    }
                    for (member in classSymbol.fields()) {
                        if (isPersistable(member, accessType)) {
                            String name = propertyName(member)
                            Type propertyType =
                                    propertyType(member, entityName,
                                            qualify(path, name),
                                            defaultAccessType)
                            if (propertyType != null) {
                                names.add(name)
                                types.add(propertyType)
                            }
                        }
                    }
                }
                type = classSymbol.superclass
            }

            propertyNames = names.toArray([])
            propertyTypes = types.toArray([])
        }

        @Override
        int getPropertyIndex(String name) {
            String[] names = getPropertyNames()
            for ( int i = 0, max = names.length; i < max; i++ ) {
                if ( names[i].equals( name ) ) {
                    return i
                }
            }
            throw new PropertyNotFoundException(
                    "Unable to locate property named " + name + " on " + getName()
            )
        }

        @Override
        String getName() {
            return new String(type.sourceName())
        }

        @Override
        boolean isComponentType() {
            return true
        }

        @Override
        String[] getPropertyNames() {
            return propertyNames
        }

        @Override
        Type[] getSubtypes() {
            return propertyTypes
        }

        @Override
        boolean[] getPropertyNullability() {
            return new boolean[propertyNames.length]
        }

        @Override
        int getColumnSpan(Mapping mapping) {
            return propertyNames.length
        }
    }

    static abstract class EntityPersister extends MockEntityPersister {
        private final def typeDeclaration

        EntityPersister(String entityName, type, EclipseSessionFactory that,
                        AccessType defaultAccessType) {
            super(entityName, defaultAccessType, that)
            this.typeDeclaration = type
            initSubclassPersisters()
        }

        @Override
        boolean isSubclassPersister(MockEntityPersister entityPersister) {
            EntityPersister persister = (EntityPersister) entityPersister
            //isSubtypeOf missing in Eclipse
            return persister.typeDeclaration.isCompatibleWith(typeDeclaration)
        }

        @Override
        Type createPropertyType(String propertyPath) {
            def symbol =
                    findPropertyByPath(typeDeclaration, propertyPath,
                            defaultAccessType)
            return symbol == null ? null :
                    propertyType(symbol, getEntityName(),
                            propertyPath, defaultAccessType)
        }

    }

    static abstract class ToManyAssociationPersister extends MockCollectionPersister {
        ToManyAssociationPersister(String role,
                                   CollectionType collectionType,
                                   String targetEntityName,
                                   EclipseSessionFactory that) {
            super(role, collectionType,
                    new ManyToOneType(typeConfiguration, targetEntityName),
                    that)
        }

        @Override
        Type getElementPropertyType(String propertyPath) {
            return getElementPersister().getPropertyType(propertyPath)
        }
    }

    static abstract class ElementCollectionPersister extends MockCollectionPersister {
        private final def elementType
        private final AccessType defaultAccessType

        ElementCollectionPersister(String role,
                                   CollectionType collectionType,
                                   elementType,
                                   String propertyPath,
                                   AccessType defaultAccessType,
                                   Type elementCollectionType,
                                   EclipseSessionFactory that) {
            super(role, collectionType, elementCollectionType, that)
            this.elementType = elementType
            this.defaultAccessType = defaultAccessType
        }

        @Override
        Type getElementPropertyType(String propertyPath) {
            def symbol =
                    findPropertyByPath(elementType, propertyPath,
                            defaultAccessType)
            return symbol == null ? null :
                    propertyType(symbol, getOwnerEntityName(),
                            propertyPath, defaultAccessType)
        }
    }

    private static String simpleTypeName(type) {
        return new String((char[]) type.sourceName())
    }

    static String simpleMethodName(binding) {
        return new String((char[]) binding.selector)
    }

    static String simpleVariableName(binding) {
        return new String((char[]) binding.name)
    }

    static String qualifiedTypeName(type) {
        def pkgPart = new String((char[]) type.qualifiedPackageName());
        return pkgPart.isEmpty()
                ? new String((char[]) type.qualifiedSourceName())
                : pkgPart + "." + new String((char[]) type.qualifiedSourceName())
    }

//    static String qualifiedMethodName(binding) {
//        return qualifiedTypeName(binding.declaringClass) +
//                "." + new String((char[]) binding.selector)
//    }

    static boolean hasAnnotation(annotations, String name) {
        for (ann in annotations.getAnnotations()) {
            if (qualifiedTypeName(ann.getAnnotationType()) == name) {
                return true
            }
        }
        return false
    }

    static def getAnnotation(annotations, String name) {
        for (ann in annotations.getAnnotations()) {
            if (qualifiedTypeName(ann.getAnnotationType()) == name) {
                return ann
            }
        }
        return null
    }


    private static AccessType getDefaultAccessType(type) {
        while (type != null && type.metaClass.hasProperty(type, "superclass")) {
            def classSymbol = type
            for (member in classSymbol.methods()) {
                if (isId(member)) {
                    return AccessType.PROPERTY
                }
            }
            for (member in classSymbol.fields()) {
                if (isId(member)) {
                    return AccessType.FIELD
                }
            }
            type = classSymbol.superclass
        }
        return AccessType.FIELD
    }

    private def findEntityClass(String entityName) {
        if (entityName == null) {
            return null;
        }
        else if (entityName.indexOf('.')>0) {
            def type = findClassByQualifiedName(entityName)
            return isEntity(type) ? type : null
        }
        else {
            def type = unit.scope.getType(entityName.toCharArray())
            return !missing(type) && isEntity(type) && getEntityName(type) == entityName ? type : null
        }
    }

    private static def findProperty(type, String property,
                                    AccessType defaultAccessType) {
        //iterate up the superclass hierarchy
        while (type != null && type.metaClass.hasProperty(type, "superclass")) {
            def classSymbol = type
            if (isMappedClass(type)) { //ignore unmapped intervening classes
                AccessType accessType =
                        getAccessType(type, defaultAccessType)
                for (member in classSymbol.methods()) {
                    if (isPersistable(member, accessType) &&
                            property == propertyName(member)) {
                        return member
                    }
                }
                for (member in classSymbol.fields()) {
                    if (isPersistable(member, accessType) &&
                            property == propertyName(member)) {
                        return member
                    }
                }
            }
            type = classSymbol.superclass
        }
        return null
    }

    private static String propertyName(def symbol) {
        if (symbol.getClass().simpleName == "MethodBinding") {
            String name = simpleMethodName(symbol)
            if (name.startsWith("get")) {
                name = name.substring(3)
            }
            else if (name.startsWith("is")) {
                name = name.substring(2)
            }
            return Introspector.decapitalize(name)
        }
        else if (symbol.getClass().simpleName == "FieldBinding") {
            return simpleVariableName(symbol)
        }
        else {
            return null
        }
    }

    private static boolean isPersistable(member, AccessType accessType) {
        if (isStatic(member) || isTransient(member)) {
            return false
        }
        else if (member.getClass().simpleName == "FieldBinding") {
            return accessType == AccessType.FIELD
                || hasAnnotation(member, jpa("Access"))
        }
        else if (member.getClass().simpleName == "MethodBinding") {
            return isGetterMethod(member)
                && (accessType == AccessType.PROPERTY
                    || hasAnnotation(member, jpa("Access")))
        }
        else {
            return false
        }
    }

    private static boolean isGetterMethod(method) {
        if (method.parameters.length != 0) {
            return false
        }
        String methodName = simpleMethodName(method)
        def returnType = method.returnType
        return methodName.startsWith("get") && returnType.id != 6
            || methodName.startsWith("is") && returnType.id == 5
    }

    private static def getMemberType(binding) {
        if (binding.getClass().simpleName == "MethodBinding") {
            return binding.returnType
        }
        else if (binding.getClass().simpleName == "FieldBinding") {
            return binding.type
        }
        else {
            return binding
        }
    }

    private static boolean isStatic(member) {
        if ((member.modifiers & 0x0008) != 0) {
            return true
        }
        return false
    }

    private static boolean isTransient(member) {
        if ((member.modifiers & 0x0080) != 0) {
            return true
        }
        return hasAnnotation(member, jpa("Transient"))
    }

    private static boolean isEnumProperty(Binding member) {
        return hasAnnotation(member, jpa("Enumerated")) ||
                getMemberType(member).isEnum();
    }

    private static boolean isEmbeddableType(type) {
        return hasAnnotation(type, jpa("Embeddable"))
    }

    private static boolean isEmbeddedProperty(member) {
        return hasAnnotation(member, jpa("Embedded")) ||
                hasAnnotation(getMemberType(member), jpa("Embeddable"))
    }

    private static boolean isElementCollectionProperty(member) {
        return hasAnnotation(member, jpa("ElementCollection"))
    }

    private static boolean isToOneAssociation(member) {
        return hasAnnotation(member, jpa("ManyToOne")) ||
                hasAnnotation(member, jpa("OneToOne"))
    }

    private static boolean isToManyAssociation(member) {
        return hasAnnotation(member, jpa("ManyToMany")) ||
                hasAnnotation(member, jpa("OneToMany"))
    }

    private static def toOneAnnotation(member) {
        def manyToOne = getAnnotation(member, jpa("ManyToOne"))
        if (manyToOne != null) return manyToOne
        def oneToOne = getAnnotation(member, jpa("OneToOne"))
        if (oneToOne != null) return oneToOne
        return null
    }

    private static def toManyAnnotation(member) {
        def manyToMany = getAnnotation(member, jpa("ManyToMany"))
        if (manyToMany != null) return manyToMany
        def oneToMany = getAnnotation(member, jpa("OneToMany"))
        if (oneToMany != null) return oneToMany
        return null
    }

    private static def getCollectionElementType(property) {
        def memberType = getMemberType(property)
        if (memberType.metaClass.hasProperty(memberType, "arguments")) {
            def args = memberType.arguments
            return args.length > 0 ? args[args.length - 1] : null
        }
        return null
    }

    private static Object getAnnotationMember(annotation,
                                              String memberName) {
        for (pair in annotation.getElementValuePairs()) {
            if (simpleMethodName(pair.binding) == memberName) {
                return pair.value
            }
        }
        return null
    }

    static String getToOneTargetEntity(property) {
        def annotation = toOneAnnotation(property)
        def classType = getAnnotationMember(annotation, "targetEntity")
        return classType == null || classType.id == 6 ?
                //entity names are unqualified class names
                simpleTypeName(getMemberType(property)) :
                simpleTypeName(classType)
    }

    private static String getToManyTargetEntityName(property) {
        def annotation = toManyAnnotation(property)
        def classType = getAnnotationMember(annotation, "targetEntity")
        return classType == null || classType.id == 6 ?
                //entity names are unqualified class names
                simpleTypeName(getCollectionElementType(property)) :
                simpleTypeName(classType)
    }

    private static def getElementCollectionElementType(property) {
        def annotation = getAnnotation(property,
                jpa("ElementCollection"))
        def classType = getAnnotationMember(annotation, "getElementCollectionClass")
        return classType == null || classType.id == 6 ?
                getCollectionElementType(property) :
                classType
    }

    private static boolean isMappedClass(type) {
        return hasAnnotation(type, jpa("Entity")) ||
                hasAnnotation(type, jpa("Embeddable")) ||
                hasAnnotation(type, jpa("MappedSuperclass"))
    }

    private static boolean isEntity(member) {
        return member!=null
            && hasAnnotation(member, jpa("Entity"))
    }

    private static boolean isId(member) {
        return hasAnnotation(member, jpa("Id"))
    }

    private static String getEntityName(type) {
        def entityAnnotation =
                getAnnotation(type, jpa("Entity"))
        if (entityAnnotation == null) {
            //not an entity!
            return null
        }
        String name = getAnnotationMember(entityAnnotation, "name")
        //entity names are unqualified class names
        return name == null ? simpleTypeName(type) : name
    }

    private static AccessType getAccessType(type,
                                            AccessType defaultAccessType) {
        def annotation =
                getAnnotation(type, jpa("Access"))
        if (annotation == null) {
            return defaultAccessType
        }
        else {
            def member = getAnnotationMember(annotation, "value")
            if (member == null) {
                return defaultAccessType //does not occur
            }
            switch (simpleVariableName(member)) {
                case "PROPERTY":
                    return AccessType.PROPERTY
                case "FIELD":
                    return AccessType.FIELD
                default:
                    throw new IllegalStateException()
            }
        }
    }

    @Override
    protected boolean isSubtype(String entityName, String subtypeEntityName) {
        return findEntityClass(entityName).isSubtypeOf(findEntityClass(subtypeEntityName), false)
    }

    @Override
    boolean isEntityDefined(String entityName) {
        return findEntityClass(entityName) != null
    }

    @Override
    boolean isAttributeDefined(String entityName, String fieldName) {
        def entityClass = findEntityClass(entityName)
        return entityClass != null
            && findPropertyByPath(entityClass, fieldName, getDefaultAccessType(entityClass)) != null
    }

    @Override
    boolean isClassDefined(String qualifiedName) {
        return findClassByQualifiedName(qualifiedName) != null
    }

    @Override
    boolean isFieldDefined(String qualifiedClassName, String fieldName) {
        def type = findClassByQualifiedName(qualifiedClassName)
        if (type == null) return false
        for (field in type.fields()) {
            if (simpleVariableName(field) == fieldName) {
                return true
            }
        }
        return false
    }

    @Override
    boolean isConstructorDefined(String qualifiedClassName,
                                 List<Type> argumentTypes) {
        def symbol = findClassByQualifiedName(qualifiedClassName)
        if (symbol == null) return false
        for (method in symbol.methods()) {
            if (method.isConstructor() &&
                    method.parameters.length == argumentTypes.size()) {
                boolean argumentsCheckOut = true
                for (int i=0; i<argumentTypes.size(); i++) {
                    Type argType = argumentTypes.get(i)
                    def paramType = method.parameters[i]
                    if (paramType.isPrimitiveType()) {
                        Class primitive
                        try {
                            primitive = toPrimitiveClass(argType.getReturnedClass())
                        }
                        catch (ignored) {
                            continue
                        }
                        if (!toPrimitiveClass(paramType).equals(primitive)) {
                            argumentsCheckOut = false
                            break
                        }
                    }
                    else {
                        def argTypeClass
                        if (argType instanceof EntityType) {
                            String entityName = argType.getAssociatedEntityName()
                            argTypeClass = findEntityClass(entityName)
                        }
//                        else if (argType instanceof CompositeCustomType) {
//                            argTypeClass = argType.getUserType().type
//                        }
                        else if (argType instanceof BasicType) {
                            String className
                            //sadly there is no way to get the classname
                            //from a Hibernate Type without trying to load
                            //the class!
                            try {
                                className = argType.getReturnedClass().getName()
                            }
                            catch (ignored) {
                                continue
                            }
                            argTypeClass = findClassByQualifiedName(className)
                        }
                        else {
                            //TODO: what other Hibernate Types do we
                            //      need to consider here?
                            continue
                        }
                        if (argTypeClass != null &&
                                //isSubtypeOf missing in Eclipse
                                !argTypeClass.isCompatibleWith(paramType)) {
                            argumentsCheckOut = false
                            break
                        }
                    }
                }
                if (argumentsCheckOut) return true //matching constructor found!
            }
        }
        return false
    }

    private static Class toPrimitiveClass(param) {
        switch (param.id) {
            case 10:
                return int.class
            case 7:
                return long.class
            case 4:
                return short.class
            case 3:
                return byte.class
            case 9:
                return float.class
            case 8:
                return double.class
            case 5:
                return boolean.class
            case 2:
                return char.class
            default:
                return Object.class
        }
    }

    def findClassByQualifiedName(String path) {
        char[][] name =
                stream(path.split("\\.")).map {s -> s.toCharArray()}.toArray {len -> new char[len][]}
        def type = unit.scope.getType(name, name.length)
        return missing(type) ? null : type
    }

    private static boolean missing(type) {
        return type.getClass().simpleName == "MissingTypeBinding"
            || type.getClass().simpleName == "ProblemReferenceBinding"
    }

}
