package org.hibernate.query.validator;

import org.eclipse.jdt.internal.compiler.Compiler;
import org.eclipse.jdt.internal.compiler.apt.dispatch.BaseProcessingEnvImpl;
import org.eclipse.jdt.internal.compiler.ast.*;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;
import org.eclipse.jdt.internal.compiler.lookup.*;
import org.hibernate.QueryException;
import org.hibernate.hql.internal.ast.ParseErrorHandler;
import org.hibernate.type.*;

import javax.persistence.AccessType;
import java.beans.Introspector;
import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.stream;
import static org.eclipse.jdt.core.compiler.CharOperation.charToString;
import static org.hibernate.internal.util.StringHelper.*;

class ECJSessionFactory extends MockSessionFactory {

    static Compiler compiler;

    static void initialize(BaseProcessingEnvImpl processingEnv) {
        compiler = processingEnv.getCompiler();
    }

    ECJSessionFactory(ParseErrorHandler handler) {
        super(handler);
    }

    @Override
    MockEntityPersister createMockEntityPersister(String entityName) {
        MockEntityPersister cached = entityPersisters.get(entityName);
        if (cached!=null) return cached;

        EntityPersister persister;
        TypeDeclaration type = findEntityClass(entityName);
        if (type==null) {
            return null;
        }
        else {
            persister = new EntityPersister(entityName, type);
        }

        entityPersisters.put(entityName, persister);
        return persister;
    }

    @Override
    MockCollectionPersister createMockCollectionPersister(String role) {
        MockCollectionPersister cached = collectionPersisters.get(role);
        if (cached!=null) return cached;

        MockCollectionPersister persister;
        String entityName = root(role); //only works because entity names don't contain dots
        String propertyPath = unroot(role);
        TypeBinding entityClass = findEntityClass(entityName).binding;
        AccessType defaultAccessType = getDefaultAccessType(entityClass);
        Binding property =
                findPropertyByPath(entityClass, propertyPath, defaultAccessType);
        CollectionType collectionType = collectionType(getMemberType(property), role);
        if (isToManyAssociation(property)) {
            persister = new ToManyAssociationPersister(role, collectionType,
                    getToManyTargetEntityName(property));
        }
        else if (isElementCollectionProperty(property)) {
            TypeBinding elementType =
                    getElementCollectionElementType(property);
            persister = new ElementCollectionPersister(role, collectionType,
                    elementType, propertyPath, defaultAccessType);
        }
        else {
            return null;
        }

        collectionPersisters.put(role, persister);
        return persister;
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
            return new CompositeCustomType(
                    new Component(memberType,
                            entityName, path, defaultAccessType)) {
                @Override
                public String getName() {
                    return simpleName(memberType);
                }
            };
        }
        else if (isToOneAssociation(member)) {
            String targetEntity = getToOneTargetEntity(member);
            return typeHelper.entity(targetEntity);
        }
        else if (isToManyAssociation(member)) {
            return collectionType(memberType, qualify(entityName, path));
        }
        else if (isElementCollectionProperty(member)) {
            return collectionType(memberType, qualify(entityName,path));
        }
        else {
            Type result = typeResolver.basic(qualifiedName(memberType));
            return result == null ? UNKNOWN_TYPE : result;
        }
    }

    private static Type elementCollectionElementType(TypeBinding elementType,
                                                     String role, String path,
                                                     AccessType defaultAccessType) {
        if (isEmbeddableType(elementType)) {
            return new CompositeCustomType(
                    new Component(elementType,
                            role, path, defaultAccessType)) {
                @Override
                public String getName() {
                    return simpleName(elementType);
                }
            };
        }
        else {
            return typeResolver.basic(qualifiedName(elementType));
        }
    }

    private static CollectionType collectionType(
            TypeBinding type, String role) {
        return createCollectionType(role, simpleName(type.actualType()));
    }

    private static class Component extends MockComponent {
        private String[] propertyNames;
        private Type[] propertyTypes;
        TypeBinding type;

        Component(TypeBinding type,
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
        public String[] getPropertyNames() {
            return propertyNames;
        }

        @Override
        public Type[] getPropertyTypes() {
            return propertyTypes;
        }

    }

    private class EntityPersister extends MockEntityPersister {
        private final TypeDeclaration type;

        private EntityPersister(String entityName, TypeDeclaration type) {
            super(entityName, getDefaultAccessType(type.binding),
                    ECJSessionFactory.this);
            this.type = type;
            initSubclassPersisters();
        }

        @Override
        boolean isSubclassPersister(MockEntityPersister entityPersister) {
            EntityPersister persister = (EntityPersister) entityPersister;
            return isSubclass(persister.type.binding, type.binding);
        }

        @Override
        public Type getPropertyType(String propertyPath)
                throws QueryException {
            Type cached = properties.get(propertyPath);
            if (cached!=null) return cached;

            Binding symbol =
                    findPropertyByPath(type.binding, propertyPath,
                            defaultAccessType);
            Type result = symbol == null ? null :
                    propertyType(symbol, getEntityName(),
                            propertyPath, defaultAccessType);

            if (result == null) {
                //check subclasses, needed for treat()
                result = getSubclassPropertyType(propertyPath);
            }

            properties.put(propertyPath, result);
            return result;
        }

    }

    private class ToManyAssociationPersister extends MockCollectionPersister {
        ToManyAssociationPersister(String role,
                                   CollectionType collectionType,
                                   String targetEntityName) {
            super(role, collectionType,
                    typeHelper.entity(targetEntityName),
                    ECJSessionFactory.this);
        }

        @Override
        Type getElementPropertyType(String propertyPath) {
            return getElementPersister().getPropertyType(propertyPath);
        }
    }

    private class ElementCollectionPersister extends MockCollectionPersister {
        private final TypeBinding elementType;
        private final AccessType defaultAccessType;

        ElementCollectionPersister(String role,
                                   CollectionType collectionType,
                                   TypeBinding elementType,
                                   String propertyPath,
                                   AccessType defaultAccessType) {
            super(role, collectionType,
                    elementCollectionElementType(elementType, role,
                            propertyPath, defaultAccessType),
                    ECJSessionFactory.this);
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
        return charToString(type.qualifiedPackageName())
                + "."  +charToString(type.qualifiedSourceName());
    }

    static String qualifiedName(MethodBinding binding) {
        return qualifiedName(binding.declaringClass)
                + "." + charToString(binding.selector);
    }

    private static boolean hasAnnotation(Binding annotations, String name) {
        for (AnnotationBinding ann: annotations.getAnnotations()) {
            if (qualifiedName(ann.getAnnotationType())
                    .equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static AnnotationBinding getAnnotation(Binding annotations, String name) {
        for (AnnotationBinding ann: annotations.getAnnotations()) {
            if (qualifiedName(ann.getAnnotationType()).equals(name)) {
                return ann;
            }
        }
        return null;
    }

    private static boolean isSubclass(TypeBinding subtype, TypeBinding type) {
        return subtype.isSubtypeOf(type);
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

    private static TypeDeclaration findEntityClass(String entityName) {
        for (CompilationUnitDeclaration unit: compiler.unitsToProcess) {
            for (TypeDeclaration type: unit.types) {
                if (isEntity(type.binding)
                        && getEntityName(type.binding).equals(entityName)) {
                    return type;
                }
            }
        }
        return null;
    }

    private static Binding findProperty(TypeBinding type, String propertyName,
                                AccessType defaultAccessType) {
        //iterate up the superclass hierarchy
        while (type instanceof SourceTypeBinding) {
            SourceTypeBinding classSymbol = (SourceTypeBinding) type;
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
            type = classSymbol.superclass;
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
                    || hasAnnotation(member, "javax.persistence.Access");
        }
        else if (member instanceof MethodBinding) {
            return isGetterMethod((MethodBinding) member)
                    && (accessType == AccessType.PROPERTY
                    || hasAnnotation(member, "javax.persistence.Access"));
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
            if ((((FieldBinding) member).modifiers & ClassFileConstants.AccStatic) != 0) {
                return true;
            }
        }
        else if (member instanceof MethodBinding) {
            if ((((MethodBinding) member).modifiers & ClassFileConstants.AccStatic) != 0) {
                return false;
            }
        }
        return false;
    }

    private static boolean isTransient(Binding member) {
        if (member instanceof FieldBinding) {
            if ((((FieldBinding) member).modifiers & ClassFileConstants.AccTransient) != 0) {
                return true;
            }
        }
        return hasAnnotation(member, "javax.persistence.Transient");
    }

    private static boolean isEmbeddableType(TypeBinding type) {
        return hasAnnotation(type, "javax.persistence.Embeddable");
    }

    private static boolean isEmbeddedProperty(Binding member) {
        return hasAnnotation(member, "javax.persistence.Embedded")
                || hasAnnotation(getMemberType(member), "javax.persistence.Embeddable");
    }

    private static boolean isElementCollectionProperty(Binding member) {
        return hasAnnotation(member, "javax.persistence.ElementCollection");
    }

    private static boolean isToOneAssociation(Binding member) {
        return hasAnnotation(member, "javax.persistence.ManyToOne")
                || hasAnnotation(member, "javax.persistence.OneToOne");
    }

    private static boolean isToManyAssociation(Binding member) {
        return hasAnnotation(member, "javax.persistence.ManyToMany")
                || hasAnnotation(member, "javax.persistence.OneToMany");
    }

    private static AnnotationBinding toOneAnnotation(Binding member) {
        AnnotationBinding manyToOne =
                getAnnotation(member, "javax.persistence.ManyToOne");
        if (manyToOne!=null) return manyToOne;
        AnnotationBinding oneToOne =
                getAnnotation(member, "javax.persistence.OneToOne");
        if (oneToOne!=null) return oneToOne;
        return null;
    }

    private static AnnotationBinding toManyAnnotation(Binding member) {
        AnnotationBinding manyToMany =
                getAnnotation(member, "javax.persistence.ManyToMany");
        if (manyToMany!=null) return manyToMany;
        AnnotationBinding oneToMany =
                getAnnotation(member, "javax.persistence.OneToMany");
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

    static String getToOneTargetEntity(Binding property) {
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
                "javax.persistence.ElementCollection");
        TypeBinding classType = (TypeBinding)
                getAnnotationMember(annotation, "getElementCollectionClass");
        return classType == null || classType.id == TypeIds.T_void ?
                getCollectionElementType(property) :
                classType;
    }

    private static boolean isMappedClass(TypeBinding type) {
        return hasAnnotation(type, "javax.persistence.Entity")
                || hasAnnotation(type, "javax.persistence.Embeddable")
                || hasAnnotation(type, "javax.persistence.MappedSuperclass");
    }

    private static boolean isEntity(TypeBinding member) {
        return hasAnnotation(member, "javax.persistence.Entity");
    }

    private static boolean isId(Binding member) {
        return hasAnnotation(member, "javax.persistence.Id");
    }

    private static String getEntityName(TypeBinding type) {
        AnnotationBinding entityAnnotation =
                getAnnotation(type, "javax.persistence.Entity");
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
                getAnnotation(type, "javax.persistence.Access");
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
    boolean isClassDefined(String qualifiedName) {
        return findClassByQualifiedName(qualifiedName)!=null;
    }

    @Override
    boolean isFieldDefined(String qualifiedClassName, String fieldName) {
        TypeDeclaration type = findClassByQualifiedName(qualifiedClassName);
        if (type==null) return false;
        for (FieldDeclaration field: type.fields) {
            if (simpleName(field.binding).equals(fieldName)) {
                return true;
            }
        }
        return false;
    }

    @Override
    boolean isConstructorDefined(String qualifiedClassName,
                                 List<Type> argumentTypes) {
        TypeDeclaration symbol = findClassByQualifiedName(qualifiedClassName);
        if (symbol==null) return false;
        for (AbstractMethodDeclaration cons : symbol.methods) {
            if (cons instanceof ConstructorDeclaration) {
                ConstructorDeclaration constructor = (ConstructorDeclaration) cons;
                if (constructor.arguments.length == argumentTypes.size()) {
                    boolean argumentsCheckOut = true;
                    for (int i = 0; i < argumentTypes.size(); i++) {
                        Type type = argumentTypes.get(i);
                        Argument param = constructor.arguments[i];
                        TypeBinding typeClass;
                        if (type instanceof EntityType) {
                            String entityName = ((EntityType) type).getAssociatedEntityName();
                            typeClass = findEntityClass(entityName).binding;
                        }
                        else if (type instanceof CompositeCustomType) {
                            typeClass = ((Component) ((CompositeCustomType) type).getUserType()).type;
                        }
                        else if (type instanceof BasicType) {
                            String className;
                            //sadly there is no way to get the classname
                            //from a Hibernate Type without trying to load
                            //the class!
                            try {
                                className = type.getReturnedClass().getName();
                            } catch (Exception e) {
                                continue;
                            }
                            typeClass = findClassByQualifiedName(className).binding;
                        }
                        else {
                            //TODO: what other Hibernate Types do we
                            //      need to consider here?
                            continue;
                        }
                        if (typeClass != null
                                && param.type.resolvedType.isSubtypeOf(typeClass)) {
                            argumentsCheckOut = false;
                            break;
                        }
                    }
                    if (argumentsCheckOut) return true; //matching constructor found!
                }
            }
        }
        return false;
    }

    static TypeDeclaration findClassByQualifiedName(String path) {
        for (CompilationUnitDeclaration unit: compiler.unitsToProcess) {
            for (TypeDeclaration type: unit.types) {
                if (isEntity(type.binding)
                        && qualifiedName(type.binding).equals(path)) {
                    return type;
                }
            }
        }
        return null;
    }


}
