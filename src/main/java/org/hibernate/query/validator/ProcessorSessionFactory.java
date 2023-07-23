package org.hibernate.query.validator;

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
import org.hibernate.type.descriptor.jdbc.JdbcType;
import org.hibernate.type.descriptor.jdbc.VarcharJdbcType;
import org.hibernate.type.internal.BasicTypeImpl;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import jakarta.persistence.AccessType;
import java.beans.Introspector;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.stream;
import static org.hibernate.internal.util.StringHelper.qualify;
import static org.hibernate.internal.util.StringHelper.root;
import static org.hibernate.internal.util.StringHelper.split;
import static org.hibernate.internal.util.StringHelper.unroot;

/**
 * @author Gavin King
 */
public abstract class ProcessorSessionFactory extends MockSessionFactory {

    static final Mocker<ProcessorSessionFactory> instance = Mocker.variadic(ProcessorSessionFactory.class);
    private static final Mocker<Component> component = Mocker.variadic(Component.class);
    private static final Mocker<ToManyAssociationPersister> toManyPersister = Mocker.variadic(ToManyAssociationPersister.class);
    private static final Mocker<ElementCollectionPersister> collectionPersister = Mocker.variadic(ElementCollectionPersister.class);
    private static final Mocker<EntityPersister> entityPersister = Mocker.variadic(EntityPersister.class);

    private static final CharSequence jakartaPersistence = new StringBuilder("jakarta").append('.').append("persistence");
    private static final CharSequence javaxPersistence = new StringBuilder("javax").append('.').append("persistence");

    private final Elements elementUtil;
    private final Types typeUtil;

    public ProcessorSessionFactory(ProcessingEnvironment processingEnv) {
        elementUtil = processingEnv.getElementUtils();
        typeUtil = processingEnv.getTypeUtils();
    }

    @Override
    MockEntityPersister createMockEntityPersister(String entityName) {
        TypeElement type = findEntityClass(entityName);
        return type == null ? null : entityPersister.make(entityName, type, this);
    }

    @Override
    MockCollectionPersister createMockCollectionPersister(String role) {
        String entityName = root(role); //only works because entity names don't contain dots
        String propertyPath = unroot(role);
        TypeElement entityClass = findEntityClass(entityName);
        AccessType defaultAccessType = getDefaultAccessType(entityClass);
        Element property = findPropertyByPath(entityClass, propertyPath, defaultAccessType);
        CollectionType collectionType = collectionType(memberType(property), role);
        if (isToManyAssociation(property)) {
            return toManyPersister.make(role, collectionType,
                    getToManyTargetEntityName(property), this);
        }
        else if (isElementCollectionProperty(property)) {
            Element elementType = asElement(getElementCollectionElementType(property));
            return collectionPersister.make(role, collectionType,
                    elementType, propertyPath, defaultAccessType, this);
        }
        else {
            return null;
        }
    }

    @Override
    Type propertyType(String typeName, String propertyPath) {
        TypeElement type = findClassByQualifiedName(typeName);
        AccessType accessType = getAccessType(type, AccessType.FIELD);
        Element propertyByPath = findPropertyByPath(type, propertyPath, accessType);
        return propertyByPath == null ? null
                : propertyType(propertyByPath, typeName, propertyPath, accessType);
    }

    private static Element findPropertyByPath(TypeElement type,
            String propertyPath,
            AccessType defaultAccessType) {
        return stream(split(".", propertyPath))
                .reduce((Element) type,
                        (symbol, segment) -> dereference( defaultAccessType, symbol, segment ),
                        (last, current) -> current);
    }

    private static Element dereference(AccessType defaultAccessType, Element symbol, String segment) {
        if (symbol == null) {
            return null;
        }
        else {
            Element element = asElement(symbol.asType());
            return element instanceof TypeElement
                    ? findProperty((TypeElement) element, segment, defaultAccessType)
                    : null;
        }
    }

    static Type propertyType(Element member,
                             String entityName, String path,
                             AccessType defaultAccessType) {
        TypeMirror memberType = memberType(member);
        if (isEmbeddedProperty(member)) {
            return component.make(asElement(memberType), entityName, path, defaultAccessType);
        }
        else if (isToOneAssociation(member)) {
            String targetEntity = getToOneTargetEntity(member);
            return new ManyToOneType(typeConfiguration, targetEntity);
        }
        else if (isToManyAssociation(member)) {
            return collectionType(memberType, qualify(entityName, path));
        }
        else if (isElementCollectionProperty(member)) {
            return collectionType(memberType, qualify(entityName, path));
        }
        else if (isEnumProperty(member)) {
            return new BasicTypeImpl(new EnumJavaType(Enum.class), enumJdbcType(member));
        }
        else {
            return typeConfiguration.getBasicTypeRegistry()
                    .getRegisteredType(qualifiedName(memberType));
        }
    }

    private static JdbcType enumJdbcType(Element member) {
        VariableElement mapping = (VariableElement)
                getAnnotationMember(getAnnotation(member,"Enumerated"), "value");
        return mapping != null && mapping.getSimpleName().contentEquals("STRING")
                ? VarcharJdbcType.INSTANCE
                : IntegerJdbcType.INSTANCE;
    }

    private static Type elementCollectionElementType(TypeElement elementType,
            String role, String path,
            AccessType defaultAccessType) {
        if (isEmbeddableType(elementType)) {
            return component.make(elementType, role, path, defaultAccessType);
        }
        else {
            return typeConfiguration.getBasicTypeRegistry()
                    .getRegisteredType(qualifiedName(elementType.asType()));
        }
    }

    private static CollectionType collectionType(TypeMirror type, String role) {
        return createCollectionType(role, simpleName(type));
    }

    public static abstract class Component implements CompositeType {
        private final String[] propertyNames;
        private final Type[] propertyTypes;

        TypeElement type;

        public Component(TypeElement type,
                String entityName, String path,
                AccessType defaultAccessType) {
            this.type = type;

            List<String> names = new ArrayList<>();
            List<Type> types = new ArrayList<>();

            while (type!=null) {
                if (isMappedClass(type)) { //ignore unmapped intervening classes
                    AccessType accessType = getAccessType(type, defaultAccessType);
                    for (Element member: type.getEnclosedElements()) {
                        if (isPersistable(member, accessType)) {
                            String name = propertyName(member);
                            Type propertyType =
                                    propertyType(member, entityName,
                                            qualify(path, name), defaultAccessType);
                            if (propertyType != null) {
                                names.add(name);
                                types.add(propertyType);
                            }
                        }
                    }
                }
                type = (TypeElement) asElement(type.getSuperclass());
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
            return type.getSimpleName().toString();
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
        private final TypeElement type;
        private final javax.lang.model.util.Types typeUtil;

        public EntityPersister(String entityName, TypeElement type,
                               ProcessorSessionFactory that) {
            super(entityName, getDefaultAccessType(type), that);
            this.type = type;
            this.typeUtil = that.typeUtil;
            initSubclassPersisters();
        }

        @Override
        boolean isSubclassPersister(MockEntityPersister entityPersister) {
            EntityPersister persister = (EntityPersister) entityPersister;
            return typeUtil.isSubtype( persister.type.asType(), type.asType() );
        }

        @Override
        Type createPropertyType(String propertyPath) {
            Element symbol = findPropertyByPath(type, propertyPath, defaultAccessType);
            return symbol == null ? null :
                    propertyType(symbol, getEntityName(), propertyPath, defaultAccessType);
        }

    }

    public abstract static class ToManyAssociationPersister extends MockCollectionPersister {
        public ToManyAssociationPersister(String role,
                                   CollectionType collectionType,
                                   String targetEntityName,
                                   ProcessorSessionFactory that) {
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
        private final TypeElement elementType;
        private final AccessType defaultAccessType;

        public ElementCollectionPersister(String role,
                CollectionType collectionType,
                TypeElement elementType,
                String propertyPath,
                AccessType defaultAccessType,
                ProcessorSessionFactory that) {
            super(role, collectionType,
                    elementCollectionElementType(elementType, role,
                            propertyPath, defaultAccessType),
                    that);
            this.elementType = elementType;
            this.defaultAccessType = defaultAccessType;
        }

        @Override
        Type getElementPropertyType(String propertyPath) {
            Element symbol = findPropertyByPath(elementType, propertyPath, defaultAccessType);
            return symbol == null ? null :
                    propertyType(symbol, getOwnerEntityName(), propertyPath, defaultAccessType);
        }
    }

    @Override
    boolean isEntityDefined(String entityName) {
        return findEntityClass(entityName) != null;
    }

    @Override
    String qualifyName(String entityName) {
        TypeElement entityClass = findEntityClass(entityName);
        return entityClass == null ? null : entityClass.getSimpleName().toString();
    }

    @Override
    boolean isAttributeDefined(String entityName, String fieldName) {
        TypeElement entityClass = findEntityClass(entityName);
        return entityClass != null
            && findPropertyByPath(entityClass, fieldName, getDefaultAccessType(entityClass)) != null;
    }

    private TypeElement findEntityClass(String entityName) {
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

    private TypeElement findEntityByQualifiedName(String entityName) {
        TypeElement type = findClassByQualifiedName(entityName);
        return type != null && isEntity(type) ? type : null;
    }

    //Needed only for ECJ
    private final Map<String,TypeElement> entityCache = new HashMap<>();

    private TypeElement findEntityByUnqualifiedName(String entityName) {
        TypeElement cached = entityCache.get(entityName);
        if ( cached != null ) {
            return cached;
        }
        TypeElement symbol =
                findEntityByUnqualifiedName(entityName,
                        elementUtil.getModuleElement(""));
        if (symbol!=null) {
            entityCache.put(entityName, symbol);
            return symbol;
        }
        for (ModuleElement module: elementUtil.getAllModuleElements()) {
            symbol = findEntityByUnqualifiedName(entityName, module);
            if (symbol!=null) {
                entityCache.put(entityName, symbol);
                return symbol;
            }
        }
        return null;
    }

    private static TypeElement findEntityByUnqualifiedName(String entityName, ModuleElement module) {
        for (Element element: module.getEnclosedElements()) {
            if (element.getKind() == ElementKind.PACKAGE) {
                PackageElement pack = (PackageElement) element;
                try {
                    for (Element member : pack.getEnclosedElements()) {
                        if (isMatchingEntity(member, entityName)) {
                            return (TypeElement) member;
                        }
                    }
                }
                catch (Exception e) {}
            }
        }
        return null;
    }

    private static boolean isMatchingEntity(Element symbol, String entityName) {
        if (symbol.getKind() == ElementKind.CLASS) {
            TypeElement type = (TypeElement) symbol;
            return isEntity(type)
                && getEntityName(type).equals(entityName);
        }
        else {
            return false;
        }
    }

    private static Element findProperty(TypeElement type, String propertyName,
                                        AccessType defaultAccessType) {
        //iterate up the superclass hierarchy
        while (type!=null) {
            if (isMappedClass(type)) { //ignore unmapped intervening classes
                AccessType accessType = getAccessType(type, defaultAccessType);
                for (Element member: type.getEnclosedElements()) {
                    if (isMatchingProperty(member, propertyName, accessType)) {
                        return member;
                    }
                }
            }
            type = (TypeElement) asElement(type.getSuperclass());
        }
        return null;
    }

    private static boolean isMatchingProperty(Element symbol, String propertyName,
                                              AccessType accessType) {
        return isPersistable(symbol, accessType)
            && propertyName.equals(propertyName(symbol));
    }

    private static boolean isGetterMethod(ExecutableElement method) {
        if (!method.getParameters().isEmpty()) {
            return false;
        }
        else {
            Name methodName = method.getSimpleName();
            TypeMirror returnType = method.getReturnType();
            return methodName.subSequence(0,3).toString().equals("get") && returnType.getKind() != TypeKind.VOID
                || methodName.subSequence(0,2).toString().equals("is") && returnType.getKind() == TypeKind.BOOLEAN;
        }
    }

    private static boolean hasAnnotation(TypeMirror type, String annotationName) {
        return type.getKind() == TypeKind.DECLARED
            && getAnnotation(((DeclaredType) type).asElement(), annotationName)!=null;
    }

    private static boolean hasAnnotation(Element member, String annotationName) {
        return getAnnotation(member, annotationName)!=null;
    }

    private static AnnotationMirror getAnnotation(Element member, String annotationName) {
        for (AnnotationMirror mirror : member.getAnnotationMirrors()) {
            TypeElement annotationType = (TypeElement) mirror.getAnnotationType().asElement();
            if ( annotationType.getSimpleName().contentEquals(annotationName)
                    && annotationType.getNestingKind() == NestingKind.TOP_LEVEL ) {
                PackageElement pack = (PackageElement) annotationType.getEnclosingElement();
                Name packageName = pack.getQualifiedName();
                if (packageName.contentEquals(jakartaPersistence)
                        || packageName.contentEquals(javaxPersistence)) {
                    return mirror;
                }
            }
        }
        return null;
    }

    private static Object getAnnotationMember(AnnotationMirror annotation, String memberName) {
        if ( annotation == null ) {
            return null;
        }
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
                annotation.getElementValues().entrySet()) {
            if (entry.getKey().getSimpleName().contentEquals(memberName)) {
                return entry.getValue().getValue();
            }
        }
        return null;
    }

    private static boolean isMappedClass(TypeElement type) {
        return hasAnnotation(type, "Entity")
            || hasAnnotation(type, "Embeddable")
            || hasAnnotation(type, "MappedSuperclass");
    }

    private static boolean isEntity(TypeElement member) {
        return member.getKind() == ElementKind.CLASS
//            && member.getAnnotation(entityAnnotation)!=null;
            && hasAnnotation(member, "Entity");
    }

    private static boolean isId(Element member) {
        return hasAnnotation(member, "Id");
    }

    private static boolean isStatic(Element member) {
        return member.getModifiers().contains(Modifier.STATIC);
    }

    private static boolean isTransient(Element member) {
        return hasAnnotation(member, "Transient")
            || member.getModifiers().contains(Modifier.TRANSIENT);
    }

    private static boolean isEnumProperty(Element member) {
        if (hasAnnotation(member, "Enumerated")) {
            return true;
        }
        else {
            TypeMirror type = member.asType();
            if (type.getKind() == TypeKind.DECLARED) {
                DeclaredType declaredType = (DeclaredType) type;
                TypeElement typeElement = (TypeElement) declaredType.asElement();
                //TODO: something better here!
                return typeElement.getSuperclass().toString().startsWith("java.lang.Enum");
            }
            else {
                return false;
            }
        }
    }

    private static boolean isEmbeddableType(TypeElement type) {
        return hasAnnotation(type, "Embeddable");
    }

    private static boolean isEmbeddedProperty(Element member) {
        if (hasAnnotation(member, "Embedded")) {
            return true;
        }
        else {
            TypeMirror type = member.asType();
            return type.getKind() == TypeKind.DECLARED
                && hasAnnotation(type, "Embeddable");
        }
    }

    private static boolean isElementCollectionProperty(Element member) {
        return hasAnnotation(member, "ElementCollection");
    }

    private static boolean isToOneAssociation(Element member) {
        return hasAnnotation(member, "ManyToOne")
            || hasAnnotation(member, "OneToOne");
    }

    private static boolean isToManyAssociation(Element member) {
        return hasAnnotation(member, "ManyToMany")
            || hasAnnotation(member, "OneToMany");
    }

    private static AnnotationMirror toOneAnnotation(Element member) {
        AnnotationMirror manyToOne =
                getAnnotation(member, "ManyToOne");
        if (manyToOne!=null) return manyToOne;
        AnnotationMirror oneToOne =
                getAnnotation(member, "OneToOne");
        if (oneToOne!=null) return oneToOne;
        return null;
    }

    private static AnnotationMirror toManyAnnotation(Element member) {
        AnnotationMirror manyToMany =
                getAnnotation(member, "ManyToMany");
        if (manyToMany!=null) return manyToMany;
        AnnotationMirror oneToMany =
                getAnnotation(member, "OneToMany");
        if (oneToMany!=null) return oneToMany;
        return null;
    }

    private static String simpleName(TypeMirror type) {
        return type.getKind() == TypeKind.DECLARED
                ? simpleName(asElement(type))
                : type.toString();
    }

    private static String qualifiedName(TypeMirror type) {
        return type.getKind() == TypeKind.DECLARED
                ? qualifiedName(asElement(type))
                : type.toString();
    }

    private static String simpleName(Element type) {
        return type.getSimpleName().toString();
    }

    private static String qualifiedName(Element type) {
        if ( type instanceof PackageElement ) {
            return ((PackageElement) type).getQualifiedName().toString();
        }
        else if ( type instanceof TypeElement ) {
            return ((TypeElement) type).getQualifiedName().toString();
        }
        else {
            Element enclosingElement = type.getEnclosingElement();
            return enclosingElement != null
                    ? qualifiedName(enclosingElement) + '.' + simpleName(type)
                    : simpleName(type);
        }
    }

    private static AccessType getAccessType(TypeElement type,
                                            AccessType defaultAccessType) {
        AnnotationMirror annotation =
                getAnnotation(type, "Access");
        if (annotation==null) {
            return defaultAccessType;
        }
        else {
            VariableElement member = (VariableElement)
                    getAnnotationMember(annotation, "value");
            if (member==null) {
                return defaultAccessType; //does not occur
            }
            switch (member.getSimpleName().toString()) {
                case "PROPERTY":
                    return AccessType.PROPERTY;
                case "FIELD":
                    return AccessType.FIELD;
                default:
                    throw new IllegalStateException();
            }
        }
    }

    private static String getEntityName(TypeElement type) {
        if ( type == null ) {
            return null;
        }
        AnnotationMirror entityAnnotation =
                getAnnotation(type, "Entity");
        if (entityAnnotation==null) {
            //not an entity!
            return null;
        }
        else {
            String name = (String)
                    getAnnotationMember(entityAnnotation, "name");
            //entity names are unqualified class names
            return name==null ? simpleName(type) : name;
        }
    }

    private TypeMirror getCollectionElementType(Element property) {
        DeclaredType declaredType = (DeclaredType) memberType(property);
        List<? extends TypeMirror> typeArguments = declaredType.getTypeArguments();
        TypeMirror elementType = typeArguments.get(typeArguments.size()-1);
        return elementType==null
                ? elementUtil.getTypeElement("java.lang.Object").asType()
                : elementType;
    }

    private static String getToOneTargetEntity(Element property) {
        AnnotationMirror annotation = toOneAnnotation(property);
        TypeMirror classType = (TypeMirror)
                getAnnotationMember(annotation, "targetEntity");
        TypeMirror targetType =
                classType == null || classType.getKind() == TypeKind.VOID
                        ? memberType(property)
                        : classType;
        Element element = asElement(targetType);
        return element != null && element.getKind() == ElementKind.CLASS
                //entity names are unqualified class names
                ? getEntityName((TypeElement) element)
                : null;
    }

    private String getToManyTargetEntityName(Element property) {
        AnnotationMirror annotation = toManyAnnotation(property);
        TypeMirror classType = (TypeMirror)
                getAnnotationMember(annotation, "targetEntity");
        TypeMirror targetType =
                classType == null || classType.getKind() == TypeKind.VOID
                        ? getCollectionElementType(property)
                        : classType;
        Element element = asElement(targetType);
        return element != null && element.getKind() == ElementKind.CLASS
                //entity names are unqualified class names
                ? getEntityName((TypeElement) element)
                : null;
    }

    private TypeMirror getElementCollectionElementType(Element property) {
        AnnotationMirror annotation = getAnnotation(property,
                "ElementCollection");
        TypeMirror classType = (TypeMirror)
                getAnnotationMember(annotation, "getElementCollectionClass");
        return classType == null
            || classType.getKind() == TypeKind.VOID
                ? getCollectionElementType(property)
                : classType;
    }

    @Override
    protected String getSupertype(String entityName) {
        return asElement(findEntityClass(entityName).getSuperclass())
                .getSimpleName().toString();
    }

    @Override
    protected boolean isSubtype(String entityName, String subtypeEntityName) {
        return typeUtil.isSubtype( findEntityClass(entityName).asType(),
                findEntityClass(subtypeEntityName).asType());
    }

    @Override
    boolean isClassDefined(String qualifiedName) {
        return findClassByQualifiedName(qualifiedName)!=null;
    }

    @Override
    boolean isFieldDefined(String qualifiedClassName, String fieldName) {
        TypeElement type = findClassByQualifiedName(qualifiedClassName);
        return type != null
            && type.getEnclosedElements().stream()
                .anyMatch(element -> element.getKind() == ElementKind.FIELD
                        && element.getSimpleName().contentEquals(fieldName));
    }

    @Override
    boolean isConstructorDefined(String qualifiedClassName,
                                 List<org.hibernate.type.Type> argumentTypes) {
        TypeElement symbol = findClassByQualifiedName(qualifiedClassName);
        if (symbol==null) return false;
        for (Element cons: symbol.getEnclosedElements()) {
            if ( cons.getKind() == ElementKind.CONSTRUCTOR ) {
                ExecutableElement constructor = (ExecutableElement) cons;
                List<? extends VariableElement> parameters = constructor.getParameters();
                if (parameters.size()==argumentTypes.size()) {
                    boolean argumentsCheckOut = true;
                    for (int i=0; i<argumentTypes.size(); i++) {
                        org.hibernate.type.Type type = argumentTypes.get(i);
                        VariableElement param = parameters.get(i);
                        if (param.asType().getKind().isPrimitive()) {
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
                            TypeElement typeClass;
                            if (type instanceof EntityType) {
                                EntityType entityType = (EntityType) type;
                                String entityName = entityType.getAssociatedEntityName();
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
                                    && !typeUtil.isSubtype( typeClass.asType(), param.asType() ) ) {
                                argumentsCheckOut = false;
                                break;
                            }
                        }
                    }
                    if (argumentsCheckOut) {
                        return true; //matching constructor found!
                    }
                }
            }
        }
        return false;
    }

    private static Class<?> toPrimitiveClass(VariableElement param) {
        switch (param.asType().getKind()) {
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

    private TypeElement findClassByQualifiedName(String path) {
        return path == null ? null : elementUtil.getTypeElement(path);
    }

    private static AccessType getDefaultAccessType(TypeElement type) {
        //iterate up the superclass hierarchy
        while (type!=null) {
            for (Element member: type.getEnclosedElements()) {
                if (isId(member)) {
                    return member instanceof ExecutableElement
                            ? AccessType.PROPERTY
                            : AccessType.FIELD;
                }
            }
            type = (TypeElement) asElement(type.getSuperclass());
        }
        return AccessType.FIELD;
    }

    private static String propertyName(Element symbol) {
        String name = symbol.getSimpleName().toString();
        if (symbol.getKind() == ElementKind.METHOD) {
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

    private static boolean isPersistable(Element member, AccessType accessType) {
        if (isStatic(member) || isTransient(member)) {
            return false;
        }
        else if (member.getKind() == ElementKind.FIELD) {
            return accessType == AccessType.FIELD
//                || member.getAnnotation( accessAnnotation ) != null;
                || hasAnnotation(member, "Access");
        }
        else if (member.getKind() == ElementKind.METHOD) {
            return isGetterMethod((ExecutableElement) member)
                && (accessType == AccessType.PROPERTY
//                    || member.getAnnotation( accessAnnotation ) != null);
                    || hasAnnotation(member, "Access"));
        }
        else {
            return false;
        }
    }

    private static TypeMirror memberType(Element member) {
        if (member instanceof ExecutableElement) {
            return ((ExecutableElement) member).getReturnType();
        }
        else if (member instanceof VariableElement) {
            return member.asType();
        }
        else {
            throw new IllegalArgumentException("Not a member");
        }
    }

    public static Element asElement(TypeMirror type) {
        if ( type == null ) {
            return null;
        }
        else {
            switch (type.getKind()) {
                case DECLARED:
                    return ((DeclaredType)type).asElement();
                case TYPEVAR:
                    return ((TypeVariable)type).asElement();
                default:
                    return null;
            }
        }
    }
}
