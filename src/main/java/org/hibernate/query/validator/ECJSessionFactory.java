package org.hibernate.query.validator;

import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;
import org.eclipse.jdt.internal.compiler.lookup.*;
import org.hibernate.hql.internal.ast.ParseErrorHandler;
import org.hibernate.type.*;

import javax.persistence.AccessType;
import java.beans.Introspector;
import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.stream;
import static org.eclipse.jdt.core.compiler.CharOperation.charToString;
import static org.hibernate.internal.util.StringHelper.*;
import static org.hibernate.query.validator.HQLProcessor.jpa;

class ECJSessionFactory extends MockSessionFactory {

    private final CompilationUnitDeclaration unit;

    ECJSessionFactory(List<String> functionWhitelist,
                      ParseErrorHandler handler,
                      CompilationUnitDeclaration unit) {
        super(functionWhitelist, handler);
        this.unit = unit;
    }

    @Override
    MockEntityPersister createMockEntityPersister(String entityName) {
        TypeBinding type = findEntityClass(entityName);
        return type == null ? null : new EntityPersister(entityName, type);
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
            return new ToManyAssociationPersister(role, collectionType,
                    getToManyTargetEntityName(property));
        }
        else if (isElementCollectionProperty(property)) {
            TypeBinding elementType =
                    getElementCollectionElementType(property);
            return new ElementCollectionPersister(role, collectionType,
                    elementType, propertyPath, defaultAccessType);
        }
        else {
            return null;
        }
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
        private final TypeBinding type;

        private EntityPersister(String entityName, TypeBinding type) {
            super(entityName, getDefaultAccessType(type),
                    ECJSessionFactory.this);
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

    static boolean hasAnnotation(Binding annotations, String name) {
        for (AnnotationBinding ann: annotations.getAnnotations()) {
            if (qualifiedName(ann.getAnnotationType()).equals(name)) {
                return true;
            }
        }
        return false;
    }

    static AnnotationBinding getAnnotation(Binding annotations, String name) {
        for (AnnotationBinding ann: annotations.getAnnotations()) {
            if (qualifiedName(ann.getAnnotationType()).equals(name)) {
                return ann;
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
        if (entityName.indexOf('.')>0) {
            return findClassByQualifiedName(entityName);
        }
        TypeBinding type = unit.scope.getType(entityName.toCharArray());
        return !missing(type) && isEntity(type)
                && getEntityName(type).equals(entityName) ?
                type : null;
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
        return hasAnnotation(member, jpa("Transient"));
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
        return hasAnnotation(member, jpa("Entity"));
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
    boolean isClassDefined(String qualifiedName) {
        return findClassByQualifiedName(qualifiedName)!=null;
    }

    @Override
    boolean isFieldDefined(String qualifiedClassName, String fieldName) {
        SourceTypeBinding type = (SourceTypeBinding)
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
                    if (argType instanceof PrimitiveType
                            && paramType.isPrimitiveType()) {
                        Class primitive;
                        try {
                            primitive = ((PrimitiveType) argType).getPrimitiveClass();
                        } catch (Exception e) {
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
                        } else if (argType instanceof CompositeCustomType) {
                            argTypeClass = ((Component) ((CompositeCustomType) argType).getUserType()).type;
                        } else if (argType instanceof BasicType) {
                            String className;
                            //sadly there is no way to get the classname
                            //from a Hibernate Type without trying to load
                            //the class!
                            try {
                                className = argType.getReturnedClass().getName();
                            } catch (Exception e) {
                                continue;
                            }
                            argTypeClass = findClassByQualifiedName(className);
                        } else {
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

    private static Class toPrimitiveClass(TypeBinding param) {
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
