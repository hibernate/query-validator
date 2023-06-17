package org.hibernate.query.validator;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Names;
import org.hibernate.PropertyNotFoundException;
import org.hibernate.engine.spi.Mapping;
import org.hibernate.type.BasicType;
import org.hibernate.type.CollectionType;
import org.hibernate.type.CompositeType;
import org.hibernate.type.EntityType;
import org.hibernate.type.ManyToOneType;
import org.hibernate.type.Type;
import org.hibernate.type.descriptor.java.EnumJavaType;
import org.hibernate.type.descriptor.jdbc.IntegerJdbcType;
import org.hibernate.type.internal.BasicTypeImpl;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeKind;
import jakarta.persistence.AccessType;
import java.beans.Introspector;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.stream;
import static org.hibernate.internal.util.StringHelper.qualify;
import static org.hibernate.internal.util.StringHelper.root;
import static org.hibernate.internal.util.StringHelper.split;
import static org.hibernate.internal.util.StringHelper.unroot;
import static org.hibernate.query.validator.HQLProcessor.jpa;

/**
 * @author Gavin King
 */
public abstract class JavacSessionFactory extends MockSessionFactory {

    private static final Mocker<Component> component = Mocker.variadic(Component.class);
    private static final Mocker<ToManyAssociationPersister> toManyPersister = Mocker.variadic(ToManyAssociationPersister.class);
    private static final Mocker<ElementCollectionPersister> collectionPersister = Mocker.variadic(ElementCollectionPersister.class);
    private static final Mocker<EntityPersister> entityPersister = Mocker.variadic(EntityPersister.class);

    private final Names names;
    private final Types types;
    private final Symtab syms;

    public JavacSessionFactory(JavacProcessingEnvironment processingEnv) {
        final Context context = processingEnv.getContext();
        names = Names.instance(context);
        types = Types.instance(context);
        syms = Symtab.instance(context);
    }

    @Override
    MockEntityPersister createMockEntityPersister(String entityName) {
        Symbol.ClassSymbol type = findEntityClass(entityName);
        return type == null ? null :
                entityPersister.make(entityName, type, types, this);
    }

    @Override
    MockCollectionPersister createMockCollectionPersister(String role) {
        String entityName = root(role); //only works because entity names don't contain dots
        String propertyPath = unroot(role);
        Symbol.ClassSymbol entityClass = findEntityClass(entityName);
        AccessType defaultAccessType = getDefaultAccessType(entityClass);
        Symbol property =
                findPropertyByPath(entityClass, propertyPath, defaultAccessType);
        CollectionType collectionType = collectionType(memberType(property), role);
        if (isToManyAssociation(property)) {
            return toManyPersister.make(role, collectionType,
                    getToManyTargetEntityName(property), this);
        }
        else if (isElementCollectionProperty(property)) {
            Symbol.TypeSymbol elementType =
                    getElementCollectionElementType(property).tsym;
            return collectionPersister.make(role, collectionType,
                    elementType, propertyPath, defaultAccessType, this);
        }
        else {
            return null;
        }
    }

    @Override
    Type propertyType(String typeName, String propertyPath) {
        Symbol.ClassSymbol type = findClassByQualifiedName(typeName);
        AccessType accessType = getAccessType(type, AccessType.FIELD);
        Symbol propertyByPath = findPropertyByPath(type, propertyPath, accessType);
        if ( propertyByPath == null) return null;
        return propertyType(propertyByPath, typeName, propertyPath, accessType);
    }

    private static Symbol findPropertyByPath(Symbol.TypeSymbol type,
                                             String propertyPath,
                                             AccessType defaultAccessType) {
        return stream(split(".", propertyPath))
                .reduce((Symbol) type,
                        (symbol, segment) -> symbol==null ? null :
                                findProperty(getMemberType(symbol).tsym,
                                        segment, defaultAccessType),
                        (last, current) -> current);
    }

    static Type propertyType(Symbol member,
                             String entityName, String path,
                             AccessType defaultAccessType) {
        com.sun.tools.javac.code.Type memberType = getMemberType(member);
        if (isEmbeddedProperty(member)) {
            return component.make(memberType.tsym, entityName, path, defaultAccessType);
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
            Type result = typeConfiguration.getBasicTypeRegistry()
                    .getRegisteredType(qualifiedName(memberType));
            return result; //TODO: result == null ? unknownType : result;
        }
    }

    private static Type elementCollectionElementType(Symbol.TypeSymbol elementType,
                                                     String role, String path,
                                                     AccessType defaultAccessType) {
        if (isEmbeddableType(elementType)) {
            return component.make(elementType, role, path, defaultAccessType);
        }
        else {
            return typeConfiguration.getBasicTypeRegistry()
                    .getRegisteredType(qualifiedName(elementType.type));
        }
    }

    private static CollectionType collectionType(
            com.sun.tools.javac.code.Type type, String role) {
        return createCollectionType(role, simpleName(type));
    }

    public static abstract class Component implements CompositeType {
        private final String[] propertyNames;
        private final Type[] propertyTypes;

        Symbol.TypeSymbol type;

        public Component(Symbol.TypeSymbol type,
                  String entityName, String path,
                  AccessType defaultAccessType) {
            this.type = type;

            List<String> names = new ArrayList<>();
            List<Type> types = new ArrayList<>();

            while (type instanceof Symbol.ClassSymbol) {
                if (isMappedClass(type)) { //ignore unmapped intervening classes
                    AccessType accessType =
                            getAccessType(type, defaultAccessType);
                    for (Symbol member: type.members()
                            .getSymbols(symbol -> isPersistable(symbol, accessType))) {
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
                Symbol.ClassSymbol classSymbol =
                        (Symbol.ClassSymbol) type;
                com.sun.tools.javac.code.Type superclass =
                        classSymbol.getSuperclass();
                type = superclass == null ? null : superclass.tsym;
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
            return type.name.toString();
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
        private final Symbol.ClassSymbol type;
        private final Types types;

        public EntityPersister(String entityName, Symbol.ClassSymbol type, Types types,
                               JavacSessionFactory that) {
            super(entityName, getDefaultAccessType(type), that);
            this.type = type;
            this.types = types;
            initSubclassPersisters();
        }

        @Override
        boolean isSubclassPersister(MockEntityPersister entityPersister) {
            EntityPersister persister = (EntityPersister) entityPersister;
            return persister.type.isSubClass(type, types);
        }

        @Override
        Type createPropertyType(String propertyPath) {
            Symbol symbol =
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
                                   JavacSessionFactory that) {
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
        private final Symbol.TypeSymbol elementType;
        private final AccessType defaultAccessType;

        public ElementCollectionPersister(String role,
                                   CollectionType collectionType,
                                   Symbol.TypeSymbol elementType,
                                   String propertyPath,
                                   AccessType defaultAccessType,
                                   JavacSessionFactory that) {
            super(role, collectionType,
                    elementCollectionElementType(elementType, role,
                            propertyPath, defaultAccessType),
                    that);
            this.elementType = elementType;
            this.defaultAccessType = defaultAccessType;
        }

        @Override
        Type getElementPropertyType(String propertyPath) {
            Symbol symbol =
                    findPropertyByPath(elementType, propertyPath,
                            defaultAccessType);
            return symbol == null ? null :
                    propertyType(symbol, getOwnerEntityName(),
                            propertyPath, defaultAccessType);
        }
    }

    @Override
    boolean isEntityDefined(String entityName) {
        return findEntityClass(entityName) != null;
    }

    @Override
    String qualifyName(String entityName) {
        Symbol.ClassSymbol entityClass = findEntityClass(entityName);
        return entityClass == null ? null : entityClass.name.toString();
    }

    @Override
    boolean isAttributeDefined(String entityName, String fieldName) {
        Symbol.ClassSymbol entityClass = findEntityClass(entityName);
        return entityClass != null
            && findPropertyByPath(entityClass, fieldName, getDefaultAccessType(entityClass)) != null;
    }

    private Symbol.ClassSymbol findEntityClass(String entityName) {
        if (entityName == null) {
            return null;
        }
        else if (entityName.indexOf('.')>0) {
            return findEntityByQualifiedName(entityName);
        }
        else {
            return findEntityByUnqualifiedName(entityName);
        }
    }

    private Symbol.ClassSymbol findEntityByQualifiedName(String entityName) {
        Symbol.ClassSymbol type = findClassByQualifiedName(entityName);
        return isEntity(type) ? type : null;
    }

    private Symbol.ClassSymbol findEntityByUnqualifiedName(String entityName) {
        Symbol.ClassSymbol symbol = findEntityByUnqualifiedName(entityName, syms.unnamedModule);
        if (symbol!=null) {
            return symbol;
        }
        for (Symbol.ModuleSymbol module: syms.getAllModules()) {
            symbol = findEntityByUnqualifiedName(entityName, module);
            if (symbol!=null) {
                return symbol;
            }
        }
        return null;
    }

    private static Symbol.ClassSymbol findEntityByUnqualifiedName(String entityName, Symbol.ModuleSymbol module) {
        for (Symbol pack: module.enclosedPackages) {
            try {
                for (Symbol type: pack.members()
                        .getSymbols(symbol -> isMatchingEntity(symbol, entityName))) {
                    return (Symbol.ClassSymbol) type;
                }
            }
            catch (Exception e) {}
        }
        return null;
    }

    private static boolean isMatchingEntity(Symbol symbol, String entityName) {
        if (symbol instanceof Symbol.ClassSymbol) {
            Symbol.ClassSymbol type = (Symbol.ClassSymbol) symbol;
            return isEntity(type)
                && getEntityName(type).equals(entityName);
        }
        else {
            return false;
        }
    }

    private static Symbol findProperty(Symbol.TypeSymbol type, String propertyName,
                               AccessType defaultAccessType) {
        //iterate up the superclass hierarchy
        while (type instanceof Symbol.ClassSymbol) {
            if (isMappedClass(type)) { //ignore unmapped intervening classes
                AccessType accessType =
                        getAccessType(type, defaultAccessType);
                for (Symbol member: type.members()
                        .getSymbols(symbol -> isMatchingProperty(symbol, propertyName, accessType))) {
                    return member;
                }
            }
            Symbol.ClassSymbol classSymbol = (Symbol.ClassSymbol) type;
            com.sun.tools.javac.code.Type superclass = classSymbol.getSuperclass();
            type = superclass == null ? null : superclass.tsym;
        }
        return null;
    }

    private static boolean isMatchingProperty(Symbol symbol, String propertyName,
                                              AccessType accessType) {
        return isPersistable(symbol, accessType)
            && propertyName.equals(propertyName(symbol));
    }

    private static boolean isGetterMethod(Symbol.MethodSymbol method) {
        if (!method.params().isEmpty()) {
            return false;
        }
        String methodName = method.name.toString();
        TypeTag returnType = method.getReturnType().getTag();
        return methodName.startsWith("get") && returnType != TypeTag.VOID
            || methodName.startsWith("is") && returnType == TypeTag.BOOLEAN;
    }

    private static boolean hasAnnotation(Symbol member, String annotationName) {
        return getAnnotation(member, annotationName)!=null;
    }

    static AnnotationMirror getAnnotation(Symbol member, String annotationName) {
        for (AnnotationMirror mirror : member.getAnnotationMirrors()) {
            if (qualifiedName((com.sun.tools.javac.code.Type.ClassType) mirror.getAnnotationType())
                    .equals(annotationName)) {
                return mirror;
            }
        }
        if (annotationName.startsWith(new StringBuilder("javax.").append("persistence.").toString())) {
            annotationName = "jakarta" + annotationName.substring(5);
            for (AnnotationMirror mirror : member.getAnnotationMirrors()) {
                if (qualifiedName((com.sun.tools.javac.code.Type.ClassType) mirror.getAnnotationType())
                        .equals(annotationName)) {
                    return mirror;
                }
            }
        }

        return null;
    }

    private static Object getAnnotationMember(AnnotationMirror annotation, String memberName) {
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
                annotation.getElementValues().entrySet()) {
            if (entry.getKey().getSimpleName().toString().equals(memberName)) {
                return entry.getValue().getValue();
            }
        }
        return null;
    }

    private static boolean isMappedClass(Symbol.TypeSymbol type) {
        return hasAnnotation(type, jpa("Entity"))
            || hasAnnotation(type, jpa("Embeddable"))
            || hasAnnotation(type, jpa("MappedSuperclass"));
    }

    private static boolean isEntity(Symbol.TypeSymbol member) {
        return member instanceof Symbol.ClassSymbol
            && hasAnnotation(member, jpa("Entity"));
    }

    private static boolean isId(Symbol member) {
        return hasAnnotation(member, jpa("Id"));
    }

    private static boolean isTransient(Symbol member) {
        return hasAnnotation(member, jpa("Transient"))
            || (member.flags() & Flags.TRANSIENT)!=0;
    }

    private static boolean isEnumProperty(Symbol member) {
        return hasAnnotation(member, jpa("Enumerated"))
            || getMemberType(member).tsym.isEnum();
    }

    private static boolean isEmbeddableType(Symbol.TypeSymbol type) {
        return hasAnnotation(type, jpa("Embeddable"));
    }

    private static boolean isEmbeddedProperty(Symbol member) {
        return hasAnnotation(member, jpa("Embedded"))
            || hasAnnotation(member.type.tsym, jpa("Embeddable"));
    }

    private static boolean isElementCollectionProperty(Symbol member) {
        return hasAnnotation(member, jpa("ElementCollection"));
    }

    private static boolean isToOneAssociation(Symbol member) {
        return hasAnnotation(member, jpa("ManyToOne"))
            || hasAnnotation(member, jpa("OneToOne"));
    }

    private static boolean isToManyAssociation(Symbol member) {
        return hasAnnotation(member, jpa("ManyToMany"))
            || hasAnnotation(member, jpa("OneToMany"));
    }

    private static AnnotationMirror toOneAnnotation(Symbol member) {
        AnnotationMirror manyToOne =
                getAnnotation(member, jpa("ManyToOne"));
        if (manyToOne!=null) return manyToOne;
        AnnotationMirror oneToOne =
                getAnnotation(member, jpa("OneToOne"));
        if (oneToOne!=null) return oneToOne;
        return null;
    }

    private static AnnotationMirror toManyAnnotation(Symbol member) {
        AnnotationMirror manyToMany =
                getAnnotation(member, jpa("ManyToMany"));
        if (manyToMany!=null) return manyToMany;
        AnnotationMirror oneToMany =
                getAnnotation(member, jpa("OneToMany"));
        if (oneToMany!=null) return oneToMany;
        return null;
    }

    private static String simpleName(com.sun.tools.javac.code.Type type) {
        return type==null ? null :
                type.tsym.name.toString();
    }

    private static String qualifiedName(com.sun.tools.javac.code.Type type) {
        return type==null ? null :
                type.tsym.flatName().toString();
    }

    private static com.sun.tools.javac.code.Type memberType(Symbol property) {
        return property instanceof Symbol.MethodSymbol ?
                ((Symbol.MethodSymbol) property).getReturnType() :
                property.type;
    }

    private static AccessType getAccessType(Symbol.TypeSymbol type,
                                    AccessType defaultAccessType) {
        AnnotationMirror annotation =
                getAnnotation(type, jpa("Access"));
        if (annotation==null) {
            return defaultAccessType;
        }
        else {
            Symbol.VarSymbol member = (Symbol.VarSymbol)
                    getAnnotationMember(annotation, "value");
            if (member==null) {
                return defaultAccessType; //does not occur
            }
            switch (member.name.toString()) {
                case "PROPERTY":
                    return AccessType.PROPERTY;
                case "FIELD":
                    return AccessType.FIELD;
                default:
                    throw new IllegalStateException();
            }
        }
    }

    private static String getEntityName(Symbol.TypeSymbol type) {
        AnnotationMirror entityAnnotation =
                getAnnotation(type, jpa("Entity"));
        if (entityAnnotation==null) {
            //not an entity!
            return null;
        }
        String name = (String)
                getAnnotationMember(entityAnnotation, "name");
        //entity names are unqualified class names
        return name==null ? type.name.toString() : name;
    }

    private com.sun.tools.javac.code.Type getCollectionElementType(Symbol property) {
        com.sun.tools.javac.code.Type elementType = memberType(property).getTypeArguments().last();
        return elementType==null ? syms.objectType : elementType;
    }

    private static String getToOneTargetEntity(Symbol property) {
        AnnotationMirror annotation = toOneAnnotation(property);
        com.sun.tools.javac.code.Type.ClassType classType = (com.sun.tools.javac.code.Type.ClassType)
                getAnnotationMember(annotation, "targetEntity");
        return classType==null || classType.getKind() == TypeKind.VOID ?
                //entity names are unqualified class names
                simpleName(memberType(property)) :
                simpleName(classType);
    }

    private String getToManyTargetEntityName(Symbol property) {
        AnnotationMirror annotation = toManyAnnotation(property);
        com.sun.tools.javac.code.Type.ClassType classType = (com.sun.tools.javac.code.Type.ClassType)
                getAnnotationMember(annotation, "targetEntity");
        return classType==null || classType.getKind() == TypeKind.VOID ?
                //entity names are unqualified class names
                simpleName(getCollectionElementType(property)) :
                simpleName(classType);
    }

    private com.sun.tools.javac.code.Type getElementCollectionElementType(Symbol property) {
        AnnotationMirror annotation = getAnnotation(property,
                jpa("ElementCollection"));
        com.sun.tools.javac.code.Type classType = (com.sun.tools.javac.code.Type)
                getAnnotationMember(annotation, "getElementCollectionClass");
        return classType == null
            || classType.getKind() == TypeKind.VOID ?
                getCollectionElementType(property) :
                classType;
    }

    @Override
    protected String getSupertype(String entityName) {
        return findEntityClass(entityName).getSuperclass().tsym.name.toString();
    }

    @Override
    protected boolean isSubtype(String entityName, String subtypeEntityName) {
        return findEntityClass(entityName).isSubClass(findEntityClass(subtypeEntityName), types);
    }

    @Override
    boolean isClassDefined(String qualifiedName) {
        return findClassByQualifiedName(qualifiedName)!=null;
    }

    @Override
    boolean isFieldDefined(String qualifiedClassName, String fieldName) {
        Symbol.ClassSymbol type = findClassByQualifiedName(qualifiedClassName);
        return type != null
            && type.members().findFirst( names.fromString(fieldName) ) != null;
    }

    @Override
    boolean isConstructorDefined(String qualifiedClassName,
                                 List<org.hibernate.type.Type> argumentTypes) {
        Symbol.ClassSymbol symbol = findClassByQualifiedName(qualifiedClassName);
        if (symbol==null) return false;
        for (Symbol cons: symbol.members().getSymbols(Symbol::isConstructor)) {
            Symbol.MethodSymbol constructor = (Symbol.MethodSymbol) cons;
            if (constructor.params.length()==argumentTypes.size()) {
                boolean argumentsCheckOut = true;
                for (int i=0; i<argumentTypes.size(); i++) {
                    org.hibernate.type.Type type = argumentTypes.get(i);
                    Symbol.VarSymbol param = constructor.params.get(i);
                    if (param.type.isPrimitive()) {
                        Class<?> primitive;
                        try {
                            primitive = toPrimitiveClass( type.getReturnedClass() );
                        }
                        catch (Exception e) {
                            continue;
                        }
                        if (!toPrimitiveClass(param).equals(primitive)) {
                            argumentsCheckOut = false;
                            break;
                        }
                    }
                    else {
                        Symbol.TypeSymbol typeClass;
                        if (type instanceof EntityType) {
                            String entityName = ((EntityType) type).getAssociatedEntityName();
                            typeClass = findEntityClass(entityName);
                        }
                        //TODO:
//                        else if (type instanceof CompositeCustomType) {
//                            typeClass = ((Component) ((CompositeCustomType) type).getUserType()).type;
//                        }
                        else if (type instanceof BasicType) {
                            String className;
                            //sadly there is no way to get the classname
                            //from a Hibernate Type without trying to load
                            //the class!
                            try {
                                className = type.getReturnedClass().getName();
                            }
                            catch (Exception e) {
                                continue;
                            }
                            typeClass = findClassByQualifiedName(className);
                        }
                        else {
                            //TODO: what other Hibernate Types do we
                            //      need to consider here?
                            continue;
                        }
                        if (typeClass != null
                                && !typeClass.isSubClass(param.type.tsym, types)) {
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

    private static Class<?> toPrimitiveClass(Symbol.VarSymbol param) {
        switch (param.type.getTag()) {
            case BOOLEAN:
                return boolean.class;
            case CHAR:
                return char.class;
            case INT:
                return int.class;
            case SHORT:
                return short.class;
            case BYTE:
                return byte.class;
            case LONG:
                return long.class;
            case FLOAT:
                return float.class;
            case DOUBLE:
                return double.class;
            default:
                return Object.class;
        }
    }

    private Symbol.ClassSymbol findClassByQualifiedName(String path) {
        if ( path == null ) {
            return null;
        }
        Iterator<Symbol.ClassSymbol> it = syms.getClassesForName(names.fromString(path)).iterator();
        return it.hasNext() ? it.next() : null;
    }

    private static AccessType getDefaultAccessType(Symbol.TypeSymbol type) {
        //iterate up the superclass hierarchy
        while (type instanceof Symbol.ClassSymbol) {
            for (Symbol member: type.members().getSymbols()) {
                if (isId(member)) {
                    return member instanceof Symbol.MethodSymbol ?
                            AccessType.PROPERTY : AccessType.FIELD;
                }
            }
            Symbol.ClassSymbol classSymbol = (Symbol.ClassSymbol) type;
            com.sun.tools.javac.code.Type superclass = classSymbol.getSuperclass();
            type = superclass == null ? null : superclass.tsym;
        }
        return AccessType.FIELD;
    }

    private static String propertyName(Symbol symbol) {
        String name = symbol.name.toString();
        if (symbol instanceof Symbol.MethodSymbol) {
            if (name.startsWith("get")) {
                name = name.substring(3);
            }
            else if (name.startsWith("is")) {
                name = name.substring(2);
            }
            return Introspector.decapitalize(name);
        }
        else {
            return name;
        }
    }

    private static boolean isPersistable(Symbol member, AccessType accessType) {
        if (member.isStatic() || isTransient(member)) {
            return false;
        }
        else if (member instanceof Symbol.VarSymbol) {
            return accessType == AccessType.FIELD
                || hasAnnotation(member, jpa("Access"));
        }
        else if (member instanceof Symbol.MethodSymbol) {
            return isGetterMethod((Symbol.MethodSymbol) member)
                && (accessType == AccessType.PROPERTY
                    || hasAnnotation(member, jpa("Access")));
        }
        else {
            return false;
        }
    }

    private static com.sun.tools.javac.code.Type getMemberType(Symbol member) {
        return member instanceof Symbol.MethodSymbol ?
                ((Symbol.MethodSymbol) member).getReturnType() :
                member.type;
    }

}
