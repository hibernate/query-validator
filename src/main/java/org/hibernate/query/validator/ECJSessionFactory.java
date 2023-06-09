package org.hibernate.query.validator;

import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;
import org.eclipse.jdt.internal.compiler.lookup.*;
import org.hibernate.PropertyNotFoundException;
import org.hibernate.engine.spi.Mapping;
import org.hibernate.type.*;
import org.hibernate.type.descriptor.java.EnumJavaType;
import org.hibernate.type.descriptor.jdbc.IntegerJdbcType;
import org.hibernate.type.internal.BasicTypeImpl;

import javax.persistence.AccessType;
import java.beans.Introspector;
import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.stream;
import static org.eclipse.jdt.core.compiler.CharOperation.charToString;
import static org.hibernate.internal.util.StringHelper.*;
import static org.hibernate.query.validator.HQLProcessor.jpa;

/**
 * @author Gavin King
 */
public abstract class ECJSessionFactory extends MockSessionFactory {

    private static final Mocker<EntityPersister> entityPersister = Mocker.variadic(EntityPersister.class);
    private static final Mocker<ToManyAssociationPersister> toManyPersister = Mocker.variadic(ToManyAssociationPersister.class);
    private static final Mocker<ElementCollectionPersister> collectionPersister = Mocker.variadic(ElementCollectionPersister.class);
    private static final Mocker<Component> component = Mocker.variadic(Component.class);

    private final CompilationUnitDeclaration unit;

    public ECJSessionFactory(CompilationUnitDeclaration unit) {
        this.unit = unit;
    }

    @Override
    MockEntityPersister createMockEntityPersister(String entityName) {
        TypeBinding type = findEntityClass(entityName);
        return type == null ? null : entityPersister.make(entityName, type, this);
    }

    @Override
    MockCollectionPersister createMockCollectionPersister(String role) {
        String entityName = root(role); //only works because entity names don't contain dots
        String propertyPath = unroot(role);
        TypeBinding entityClass = findEntityClass(entityName);
        AccessType defaultAccessType = getDefaultAccessType(entityClass);
        Binding property =
                findPropertyByPath(entityClass, propertyPath, defaultAccessType);
        CollectionType collectionType = collectionType(getMemberType(property), role);
        if (isToManyAssociation(property)) {
            return toManyPersister.make(role, collectionType,
                    getToManyTargetEntityName(property), this);
        }
        else if (isElementCollectionProperty(property)) {
            TypeBinding elementType =
                    getElementCollectionElementType(property);
            return collectionPersister.make(role, collectionType, elementType,
                    propertyPath, defaultAccessType, this);
        }
        else {
            return null;
        }
    }

    @Override
    Type propertyType(String typeName, String propertyPath) {
        TypeBinding type = findClassByQualifiedName(typeName);
        AccessType accessType = getAccessType(type, AccessType.FIELD);
        Binding propertyByPath = findPropertyByPath(type, propertyPath, accessType);
        if ( propertyByPath == null) return null;
        return propertyType(propertyByPath, typeName, propertyPath, accessType);
    }

    private static Binding findPropertyByPath(TypeBinding type,
                                             String propertyPath,
                                             AccessType defaultAccessType) {
        return stream(split(".", propertyPath))
                .reduce((Binding) type,
                        (symbol, segment) -> symbol==null ? null :
                                findProperty(getMemberType(symbol),
                                        segment, defaultAccessType),
                        (last, current) -> current);
    }

    static Type propertyType(Binding member,
                             String entityName, String path,
                             AccessType defaultAccessType) {
        TypeBinding memberType = getMemberType(member);
        if (isEmbeddedProperty(member)) {
            return component.make(memberType, entityName, path, defaultAccessType);
        }
        else if (isToOneAssociation(member)) {
            String targetEntity = getToOneTargetEntity(member);
            return new ManyToOneType(typeConfiguration, targetEntity);
        }
        else if (isToManyAssociation(member)) {
            return collectionType(memberType, qualify(entityName, path));
        }
        else if (isElementCollectionProperty(member)) {
            return collectionType(memberType, qualify(entityName,path));
        }
        else if (isEnumProperty(member)) {
            return new BasicTypeImpl(new EnumJavaType(Object.class), IntegerJdbcType.INSTANCE);
        }
        else {
            Type result = typeConfiguration.getBasicTypeRegistry().getRegisteredType(qualifiedName(memberType));
            return result;// == null ? unknownType : result;
        }
    }

    private static Type elementCollectionElementType(TypeBinding elementType,
                                                     String role, String path,
                                                     AccessType defaultAccessType) {
        if (isEmbeddableType(elementType)) {
            return component.make(elementType, role, path, defaultAccessType);
        }
        else {
            return typeConfiguration.getBasicTypeRegistry().getRegisteredType(qualifiedName(elementType));
        }
    }

    private static CollectionType collectionType(
            TypeBinding type, String role) {
        return createCollectionType(role, simpleName(type.actualType()));
    }

    public static abstract class Component implements CompositeType {
        private final String[] propertyNames;
        private final Type[] propertyTypes;

        TypeBinding type;

        public Component(TypeBinding type,
                  String entityName, String path,
                  AccessType defaultAccessType) {
            this.type = type;

            List<String> names = new ArrayList<>();
            List<Type> types = new ArrayList<>();

            while (type instanceof SourceTypeBinding) {
                SourceTypeBinding classSymbol =
                        (SourceTypeBinding) type;
                if (isMappedClass(type)) { //ignore unmapped intervening classes
                    AccessType accessType =
                            getAccessType(type, defaultAccessType);
                    for (MethodBinding member: classSymbol.methods()) {
                        if (isPersistable(member, accessType)) {
                            String name = propertyName(member);
                            Type propertyType =
                                    propertyType(member, entityName,
                                            qualify(path, name),
                                            defaultAccessType);
                            if (propertyType != null) {
                                names.add(name);
                                types.add(propertyType);
                            }
                        }
                    }
                    for (FieldBinding member: classSymbol.fields()) {
                        if (isPersistable(member, accessType)) {
                            String name = propertyName(member);
                            Type propertyType =
                                    propertyType(member, entityName,
                                            qualify(path, name),
                                            defaultAccessType);
                            if (propertyType != null) {
                                names.add(name);
                                types.add(propertyType);
                            }
                        }
                    }
                }
                type = classSymbol.superclass;
            }

            propertyNames = names.toArray(new String[0]);
            propertyTypes = types.toArray(new Type[0]);
        }

        @Override
        public int getPropertyIndex(String name) {
            String[] names = getPropertyNames();
            for ( int i = 0, max = names.length; i < max; i++ ) {
                if ( names[i].equals( name ) ) {
                    return i;
                }
            }
            throw new PropertyNotFoundException(
                    "Could not resolve attribute '" + name + "' of '" + getName() + "'"
            );
        }

        @Override
        public String getName() {
            return charToString(type.sourceName());
        }

        @Override
        public boolean isComponentType() {
            return true;
        }

        @Override
        public String[] getPropertyNames() {
            return propertyNames;
        }

        @Override
        public Type[] getSubtypes() {
            return propertyTypes;
        }

        @Override
        public boolean[] getPropertyNullability() {
            return new boolean[propertyNames.length];
        }

        @Override
        public int getColumnSpan(Mapping mapping) {
            return propertyNames.length;
        }
    }

    public static abstract class EntityPersister extends MockEntityPersister {
        private final TypeBinding type;

        public EntityPersister(String entityName, TypeBinding type,
                               ECJSessionFactory that) {
            super(entityName, getDefaultAccessType(type), that);
            this.type = type;
            initSubclassPersisters();
        }

        @Override
        boolean isSubclassPersister(MockEntityPersister entityPersister) {
            EntityPersister persister = (EntityPersister) entityPersister;
            return persister.type.isCompatibleWith(type);
        }

        @Override
        Type createPropertyType(String propertyPath)  {
            Binding symbol =
                    findPropertyByPath(type, propertyPath,
                            defaultAccessType);
            return symbol == null ? null :
                    propertyType(symbol, getEntityName(),
                            propertyPath, defaultAccessType);
        }

    }

    public abstract static class ToManyAssociationPersister extends MockCollectionPersister {
        public ToManyAssociationPersister(String role,
                                          CollectionType collectionType,
                                          String targetEntityName,
                                          ECJSessionFactory that) {
            super(role, collectionType,
                    new ManyToOneType(typeConfiguration, targetEntityName),
                    that);
        }

        @Override
        Type getElementPropertyType(String propertyPath) {
            return getElementPersister().getPropertyType(propertyPath);
        }
    }

    public abstract static class ElementCollectionPersister extends MockCollectionPersister {
        private final TypeBinding elementType;
        private final AccessType defaultAccessType;

        public ElementCollectionPersister(String role,
                                          CollectionType collectionType,
                                          TypeBinding elementType,
                                          String propertyPath,
                                          AccessType defaultAccessType,
                                          ECJSessionFactory that) {
            super(role, collectionType,
                    elementCollectionElementType(elementType, role,
                            propertyPath, defaultAccessType),
                    that);
            this.elementType = elementType;
            this.defaultAccessType = defaultAccessType;
        }

        @Override
        Type getElementPropertyType(String propertyPath) {
            Binding symbol =
                    findPropertyByPath(elementType, propertyPath,
                            defaultAccessType);
            return symbol == null ? null :
                    propertyType(symbol, getOwnerEntityName(),
                            propertyPath, defaultAccessType);
        }
    }

    private static String simpleName(TypeBinding type) {
        return charToString(type.sourceName());
    }

    private static String simpleName(MethodBinding binding) {
        return charToString(binding.selector);
    }

    private static String simpleName(VariableBinding binding) {
        return charToString(binding.name);
    }

    static String qualifiedName(TypeBinding type) {
        String pkgName = charToString(type.qualifiedPackageName());
        String className = charToString(type.qualifiedSourceName());
        return pkgName.isEmpty() ? className : pkgName + "."  + className;
    }

    static String qualifiedName(MethodBinding binding) {
        return qualifiedName(binding.declaringClass)
                + "." + charToString(binding.selector);
    }

    static boolean hasAnnotation(Binding annotations, String name) {
        return getAnnotation(annotations, name)!=null;
    }

    static AnnotationBinding getAnnotation(Binding annotations, String name) {
        for (AnnotationBinding ann: annotations.getAnnotations()) {
            if (qualifiedName(ann.getAnnotationType()).equals(name)) {
                return ann;
            }
        }
        if (name.startsWith(new StringBuilder("javax.").append("persistence.").toString())) {
            name = "jakarta" + name.substring(5);
            for (AnnotationBinding ann : annotations.getAnnotations()) {
                if (qualifiedName(ann.getAnnotationType()).equals(name)) {
                    return ann;
                }
            }
        }
        return null;
    }

    private static AccessType getDefaultAccessType(TypeBinding type) {
        while (type instanceof SourceTypeBinding) {
            SourceTypeBinding classSymbol = (SourceTypeBinding) type;
            for (Binding member: classSymbol.methods()) {
                if (isId(member)) {
                    return AccessType.PROPERTY;
                }
            }
            for (Binding member: classSymbol.fields()) {
                if (isId(member)) {
                    return AccessType.FIELD;
                }
            }
            type = classSymbol.superclass;
        }
        return AccessType.FIELD;
    }

    private TypeBinding findEntityClass(String entityName) {
        if (entityName == null) {
            return null;
        }
        else if (entityName.indexOf('.')>0) {
            TypeBinding type = findClassByQualifiedName(entityName);
            return isEntity(type) ? type : null;
        }
        else {
            TypeBinding type = unit.scope.getType(entityName.toCharArray());
            return !missing(type) && isEntity(type) && getEntityName(type).equals(entityName)
                    ? type : null;
        }
//        for (CompilationUnitDeclaration unit: compiler.unitsToProcess) {
//            for (TypeDeclaration type: unit.types) {
//                if (isEntity(type.binding)
//                        && getEntityName(type.binding).equals(entityName)) {
//                    return type;
//                }
//            }
//        }
//        return null;
    }

    private static Binding findProperty(TypeBinding type, String propertyName,
                                AccessType defaultAccessType) {
        //iterate up the superclass hierarchy
        while (type instanceof ReferenceBinding) {
            ReferenceBinding classSymbol = (ReferenceBinding) type;
            if (isMappedClass(type)) { //ignore unmapped intervening classes
                AccessType accessType =
                        getAccessType(type, defaultAccessType);
                for (MethodBinding member: classSymbol.methods()) {
                    if (isPersistable(member, accessType)
                            && propertyName.equals(propertyName(member))) {
                        return member;
                    }
                }
                for (FieldBinding member: classSymbol.fields()) {
                    if (isPersistable(member, accessType)
                            && propertyName.equals(propertyName(member))) {
                        return member;
                    }
                }
            }
            type = type.superclass();
        }
        return null;
    }

    private static String propertyName(Binding symbol) {
        if (symbol instanceof MethodBinding) {
            String name = simpleName((MethodBinding) symbol);
            if (name.startsWith("get")) {
                name = name.substring(3);
            }
            else if (name.startsWith("is")) {
                name = name.substring(2);
            }
            return Introspector.decapitalize(name);
        }
        else if (symbol instanceof FieldBinding) {
            return simpleName((FieldBinding) symbol);
        }
        else {
            return null;
        }
    }

    private static boolean isPersistable(Binding member, AccessType accessType) {
        if (isStatic(member) || isTransient(member)) {
            return false;
        }
        else if (member instanceof FieldBinding) {
            return accessType == AccessType.FIELD
                    || hasAnnotation(member, jpa("Access"));
        }
        else if (member instanceof MethodBinding) {
            return isGetterMethod((MethodBinding) member)
                    && (accessType == AccessType.PROPERTY
                    || hasAnnotation(member, jpa("Access")));
        }
        else {
            return false;
        }
    }

    private static boolean isGetterMethod(MethodBinding method) {
        if (method.parameters.length!=0) {
            return false;
        }
        String methodName = simpleName(method);
        TypeBinding returnType = method.returnType;
        return methodName.startsWith("get") && returnType.id != TypeIds.T_void
                || methodName.startsWith("is") && returnType.id == TypeIds.T_boolean;
    }

    private static TypeBinding getMemberType(Binding binding) {
        if (binding instanceof MethodBinding) {
            return ((MethodBinding) binding).returnType;
        }
        else if (binding instanceof VariableBinding) {
            return ((VariableBinding) binding).type;
        }
        else {
            return (TypeBinding) binding;
        }
    }

    private static boolean isStatic(Binding member) {
        if (member instanceof FieldBinding) {
            return (((FieldBinding) member).modifiers & ClassFileConstants.AccStatic) != 0;
        }
        else if (member instanceof MethodBinding) {
            return (((MethodBinding) member).modifiers & ClassFileConstants.AccStatic) != 0;
        }
        else {
            return false;
        }
    }

    private static boolean isTransient(Binding member) {
        if (member instanceof FieldBinding) {
            if ((((FieldBinding) member).modifiers & ClassFileConstants.AccTransient) != 0) {
                return true;
            }
        }
        return hasAnnotation(member, jpa("Transient"));
    }

    private static boolean isEnumProperty(Binding member) {
        return hasAnnotation(member, jpa("Enumerated"))
            || getMemberType(member).isEnum();
    }

    private static boolean isEmbeddableType(TypeBinding type) {
        return hasAnnotation(type, jpa("Embeddable"));
    }

    private static boolean isEmbeddedProperty(Binding member) {
        return hasAnnotation(member, jpa("Embedded"))
            || hasAnnotation(getMemberType(member), jpa("Embeddable"));
    }

    private static boolean isElementCollectionProperty(Binding member) {
        return hasAnnotation(member, jpa("ElementCollection"));
    }

    private static boolean isToOneAssociation(Binding member) {
        return hasAnnotation(member, jpa("ManyToOne"))
            || hasAnnotation(member, jpa("OneToOne"));
    }

    private static boolean isToManyAssociation(Binding member) {
        return hasAnnotation(member, jpa("ManyToMany"))
            || hasAnnotation(member, jpa("OneToMany"));
    }

    private static AnnotationBinding toOneAnnotation(Binding member) {
        AnnotationBinding manyToOne =
                getAnnotation(member, jpa("ManyToOne"));
        if (manyToOne!=null) return manyToOne;
        AnnotationBinding oneToOne =
                getAnnotation(member, jpa("OneToOne"));
        if (oneToOne!=null) return oneToOne;
        return null;
    }

    private static AnnotationBinding toManyAnnotation(Binding member) {
        AnnotationBinding manyToMany =
                getAnnotation(member, jpa("ManyToMany"));
        if (manyToMany!=null) return manyToMany;
        AnnotationBinding oneToMany =
                getAnnotation(member, jpa("OneToMany"));
        if (oneToMany!=null) return oneToMany;
        return null;
    }

    private static TypeBinding getCollectionElementType(Binding property) {
        TypeBinding memberType = getMemberType(property);
        if (memberType instanceof ParameterizedTypeBinding) {
            TypeBinding[] args = ((ParameterizedTypeBinding) memberType).arguments;
            return args.length>0 ? args[args.length-1] : null;
        }
        return null;
    }

    private static Object getAnnotationMember(AnnotationBinding annotation,
                                              String memberName) {
        for (ElementValuePair pair :
                annotation.getElementValuePairs()) {
            if (simpleName(pair.binding).equals(memberName)) {
                return pair.value;
            }
        }
        return null;
    }

    private static String getToOneTargetEntity(Binding property) {
        AnnotationBinding annotation = toOneAnnotation(property);
        TypeBinding classType = (TypeBinding)
                getAnnotationMember(annotation, "targetEntity");
        return classType==null || classType.id == TypeIds.T_void ?
                //entity names are unqualified class names
                simpleName(getMemberType(property)) :
                simpleName(classType);
    }

    private static String getToManyTargetEntityName(Binding property) {
        AnnotationBinding annotation = toManyAnnotation(property);
        TypeBinding classType = (TypeBinding)
                getAnnotationMember(annotation, "targetEntity");
        return classType==null || classType.id == TypeIds.T_void ?
                //entity names are unqualified class names
                simpleName(getCollectionElementType(property)) :
                simpleName(classType);
    }

    private static TypeBinding getElementCollectionElementType(Binding property) {
        AnnotationBinding annotation = getAnnotation(property,
                jpa("ElementCollection"));
        TypeBinding classType = (TypeBinding)
                getAnnotationMember(annotation, "getElementCollectionClass");
        return classType == null || classType.id == TypeIds.T_void ?
                getCollectionElementType(property) :
                classType;
    }

    private static boolean isMappedClass(TypeBinding type) {
        return hasAnnotation(type, jpa("Entity"))
            || hasAnnotation(type, jpa("Embeddable"))
            || hasAnnotation(type, jpa("MappedSuperclass"));
    }

    private static boolean isEntity(TypeBinding member) {
        return member!=null
            && hasAnnotation(member, jpa("Entity"));
    }

    private static boolean isId(Binding member) {
        return hasAnnotation(member, jpa("Id"));
    }

    private static String getEntityName(TypeBinding type) {
        AnnotationBinding entityAnnotation =
                getAnnotation(type, jpa("Entity"));
        if (entityAnnotation==null) {
            //not an entity!
            return null;
        }
        String name = (String)
                getAnnotationMember(entityAnnotation, "name");
        //entity names are unqualified class names
        return name==null ? simpleName(type) : name;
    }

    private static AccessType getAccessType(TypeBinding type,
                                    AccessType defaultAccessType) {
        AnnotationBinding annotation =
                getAnnotation(type, jpa("Access"));
        if (annotation==null) {
            return defaultAccessType;
        }
        else {
            VariableBinding member = (VariableBinding)
                    getAnnotationMember(annotation, "value");
            if (member==null) {
                return defaultAccessType; //does not occur
            }
            switch (simpleName(member)) {
                case "PROPERTY":
                    return AccessType.PROPERTY;
                case "FIELD":
                    return AccessType.FIELD;
                default:
                    throw new IllegalStateException();
            }
        }
    }

    @Override
    protected boolean isSubtype(String entityName, String subtypeEntityName) {
        return findEntityClass(entityName).isSubtypeOf(findEntityClass(subtypeEntityName), false);
    }

    @Override
    protected String getSupertype(String entityName) {
        return charToString(findEntityClass(entityName).superclass().readableName());
    }

    @Override
    boolean isEntityDefined(String entityName) {
        return findEntityClass(entityName) != null;
    }

    @Override
    String qualifyName(String entityName) {
        TypeBinding entityClass = findEntityClass(entityName);
        return entityClass==null ? null : charToString(entityClass.readableName());
    }

    @Override
    boolean isAttributeDefined(String entityName, String fieldName) {
        TypeBinding entityClass = findEntityClass(entityName);
        return entityClass != null
            && findPropertyByPath(entityClass, fieldName, getDefaultAccessType(entityClass)) != null;
    }

    @Override
    boolean isClassDefined(String qualifiedName) {
        return findClassByQualifiedName(qualifiedName)!=null;
    }

    @Override
    boolean isFieldDefined(String qualifiedClassName, String fieldName) {
        ReferenceBinding type = (ReferenceBinding)
                findClassByQualifiedName(qualifiedClassName);
        if (type==null) return false;
        for (FieldBinding field: type.fields()) {
            if (simpleName(field).equals(fieldName)) {
                return true;
            }
        }
        return false;
    }

    @Override
    boolean isConstructorDefined(String qualifiedClassName,
                                 List<Type> argumentTypes) {
        SourceTypeBinding symbol = (SourceTypeBinding)
                findClassByQualifiedName(qualifiedClassName);
        if (symbol==null) return false;
        for (MethodBinding method : symbol.methods()) {
            if (method.isConstructor()
                    && method.parameters.length == argumentTypes.size()) {
                boolean argumentsCheckOut = true;
                for (int i = 0; i < argumentTypes.size(); i++) {
                    Type argType = argumentTypes.get(i);
                    TypeBinding paramType = method.parameters[i];
                    if (paramType.isPrimitiveType()) {
                        Class<?> primitive;
                        try {
                            primitive = toPrimitiveClass(argType.getReturnedClass());
                        }
                        catch (Exception e) {
                            continue;
                        }
                        if (!toPrimitiveClass(paramType).equals(primitive)) {
                            argumentsCheckOut = false;
                            break;
                        }
                    }
                    else {
                        TypeBinding argTypeClass;
                        if (argType instanceof EntityType) {
                            String entityName = ((EntityType) argType).getAssociatedEntityName();
                            argTypeClass = findEntityClass(entityName);
                        }
//                        else if (argType instanceof CompositeCustomType) {
//                            argTypeClass = ((Component) ((CompositeCustomType) argType).getUserType()).type;
//                        }
                        else if (argType instanceof BasicType) {
                            String className;
                            //sadly there is no way to get the classname
                            //from a Hibernate Type without trying to load
                            //the class!
                            try {
                                className = argType.getReturnedClass().getName();
                            }
                            catch (Exception e) {
                                continue;
                            }
                            argTypeClass = findClassByQualifiedName(className);
                        }
                        else {
                            //TODO: what other Hibernate Types do we
                            //      need to consider here?
                            continue;
                        }
                        if (argTypeClass != null
                                && !argTypeClass.isCompatibleWith(paramType)) {
                            argumentsCheckOut = false;
                            break;
                        }
                    }
                }
                if (argumentsCheckOut) return true; //matching constructor found!
            }
        }
        return false;
    }

    private static Class<?> toPrimitiveClass(TypeBinding param) {
        switch (param.id) {
            case TypeIds.T_int:
                return int.class;
            case TypeIds.T_long:
                return long.class;
            case TypeIds.T_short:
                return short.class;
            case TypeIds.T_byte:
                return byte.class;
            case TypeIds.T_float:
                return float.class;
            case TypeIds.T_double:
                return double.class;
            case TypeIds.T_boolean:
                return boolean.class;
            case TypeIds.T_char:
                return char.class;
            default:
                return Object.class;
        }
    }

    private TypeBinding findClassByQualifiedName(String path) {
        if ( path == null) {
            return null;
        }
        char[][] name = stream(path.split("\\."))
                .map(String::toCharArray)
                .toArray(char[][]::new);
        TypeBinding type = unit.scope.getType(name, name.length);
        return missing(type) ? null : type;
//        for (CompilationUnitDeclaration unit: compiler.unitsToProcess) {
//            for (TypeDeclaration type: unit.types) {
//                if (qualifiedName(type.binding).equals(path)) {
//                    return type;
//                }
//            }
//        }
//        return null;
    }

    private static boolean missing(TypeBinding type) {
        return type instanceof MissingTypeBinding
            || type instanceof ProblemReferenceBinding;
    }
}
