package org.hibernate.query.validator;

import com.sun.tools.javac.code.*;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Names;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeKind;
import javax.persistence.AccessType;
import java.beans.Introspector;
import java.util.ArrayList;
import java.util.Map;

class JavacHelper {

    private static Names names;
    private static Types types;
    private static Symtab syms;

    static void initialize(JavacProcessingEnvironment processingEnv) {
        Context context = processingEnv.getContext();
        names = Names.instance(context);
        types = Types.instance(context);
        syms = Symtab.instance(context);
    }

    static Symbol.ClassSymbol findEntityClass(String entityName) {
        for (Symbol.PackageSymbol pack:
                new ArrayList<>(syms.packages.values())) {
            try {
                for (Symbol type: pack.members()
                        .getElements(symbol -> isMatchingEntity(
                                symbol, entityName))) {
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

    static Symbol findProperty(Symbol.TypeSymbol type, String propertyName,
                               AccessType defaultAccessType) {
        //iterate up the superclass hierarchy
        while (type instanceof Symbol.ClassSymbol) {
            if (isMappedClass(type)) { //ignore unmapped intervening classes
                AccessType accessType =
                        getAccessType(type, defaultAccessType);
                for (Symbol member: type.members()
                        .getElements(symbol -> isMatchingProperty(
                                symbol, propertyName, accessType))) {
                    return member;
                }
            }
            Symbol.ClassSymbol classSymbol = (Symbol.ClassSymbol) type;
            Type superclass = classSymbol.getSuperclass();
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

    private static AnnotationMirror getAnnotation(Symbol member, String annotationName) {
        for (AnnotationMirror mirror : member.getAnnotationMirrors()) {
            if (qualifiedName((Type.ClassType) mirror.getAnnotationType())
                    .equals(annotationName)) {
                return mirror;
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

    static boolean isMappedClass(Symbol.TypeSymbol type) {
        return hasAnnotation(type, "javax.persistence.Entity")
            || hasAnnotation(type, "javax.persistence.Embeddable")
            || hasAnnotation(type, "javax.persistence.MappedSuperclass");
    }

    private static boolean isEntity(Symbol.TypeSymbol member) {
        return member instanceof Symbol.ClassSymbol
            && hasAnnotation(member, "javax.persistence.Entity");
    }

    private static boolean isId(Symbol member) {
        return hasAnnotation(member, "javax.persistence.Id");
    }

    static boolean isTransient(Symbol member) {
        return hasAnnotation(member, "javax.persistence.Transient")
            || (member.flags() & Flags.TRANSIENT)!=0;
    }

    static boolean isEmbeddedProperty(Symbol member) {
        return hasAnnotation(member, "javax.persistence.Embedded")
            || hasAnnotation(member.type.tsym, "javax.persistence.Embeddable");
    }

    static boolean isElementCollectionProperty(Symbol member) {
        return hasAnnotation(member, "javax.persistence.ElementCollection");
    }

    static boolean isToOneAssociation(Symbol member) {
        return hasAnnotation(member, "javax.persistence.ManyToOne")
            || hasAnnotation(member, "javax.persistence.OneToOne");
    }

    static boolean isToManyAssociation(Symbol member) {
        return hasAnnotation(member, "javax.persistence.ManyToMany")
            || hasAnnotation(member, "javax.persistence.OneToMany");
    }

    private static AnnotationMirror toOneAnnotation(Symbol member) {
        AnnotationMirror manyToOne =
                getAnnotation(member, "javax.persistence.ManyToOne");
        if (manyToOne!=null) return manyToOne;
        AnnotationMirror oneToOne =
                getAnnotation(member, "javax.persistence.OneToOne");
        if (oneToOne!=null) return oneToOne;
        return null;
    }

    private static AnnotationMirror toManyAnnotation(Symbol member) {
        AnnotationMirror manyToMany =
                getAnnotation(member, "javax.persistence.ManyToMany");
        if (manyToMany!=null) return manyToMany;
        AnnotationMirror oneToMany =
                getAnnotation(member, "javax.persistence.OneToMany");
        if (oneToMany!=null) return oneToMany;
        return null;
    }

    static String simpleName(Type type) {
        return type==null ? null :
                type.tsym.name.toString();
    }

    static String qualifiedName(Type type) {
        return type==null ? null :
                type.tsym.flatName().toString();
    }

    static Type memberType(Symbol property) {
        return property instanceof Symbol.MethodSymbol ?
                ((Symbol.MethodSymbol) property).getReturnType() :
                property.type;
    }

    static AccessType getAccessType(Symbol.TypeSymbol type,
                                    AccessType defaultAccessType) {
        AnnotationMirror annotation =
                getAnnotation(type, "javax.persistence.Access");
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

    static String getEntityName(Symbol.TypeSymbol type) {
        AnnotationMirror entityAnnotation =
                getAnnotation(type, "javax.persistence.Entity");
        if (entityAnnotation==null) {
            //not an entity!
            return null;
        }
        String name = (String)
                getAnnotationMember(entityAnnotation, "name");
        //entity names are unqualified class names
        return name==null ? type.name.toString() : name;
    }

    private static Type getCollectionElementType(Symbol property) {
        Type elementType = memberType(property).getTypeArguments().last();
        return elementType==null ? syms.objectType : elementType;
    }

    static String getToOneTargetEntity(Symbol property) {
        AnnotationMirror annotation = toOneAnnotation(property);
        Type.ClassType classType = (Type.ClassType)
                getAnnotationMember(annotation, "targetEntity");
        return classType==null || classType.getKind() == TypeKind.VOID ?
                //entity names are unqualified class names
                simpleName(memberType(property)) :
                classType.tsym.name.toString();
    }

    static String getToManyTargetEntity(Symbol property) {
        AnnotationMirror annotation = toManyAnnotation(property);
        Type.ClassType classType = (Type.ClassType)
                getAnnotationMember(annotation, "targetEntity");
        return classType==null || classType.getKind() == TypeKind.VOID ?
                //entity names are unqualified class names
                simpleName(getCollectionElementType(property)) :
                classType.tsym.name.toString();
    }

    static String getElementCollectionClass(Symbol property) {
        AnnotationMirror annotation = getAnnotation(property,
                "javax.persistence.ElementCollection");
        Type.ClassType classType = (Type.ClassType)
                getAnnotationMember(annotation, "getElementCollectionClass");
        return classType==null || classType.getKind() == TypeKind.VOID ?
                //collection elements are qualified class names
                qualifiedName(getCollectionElementType(property)) :
                classType.tsym.flatName().toString();
    }

    static Symbol.ClassSymbol findClassByQualifiedName(String path) {
        return syms.classes.get(names.fromString(path));
    }

    static boolean isAssignable(Symbol.VarSymbol param, Symbol.ClassSymbol typeClass) {
        return !typeClass.isSubClass(param.type.tsym, types);
    }

    static AccessType getDefaultAccessType(Symbol.TypeSymbol type) {
        //iterate up the superclass hierarchy
        while (type instanceof Symbol.ClassSymbol) {
            for (Symbol member: type.members().getElements()) {
                if (isId(member)) {
                    return member instanceof Symbol.MethodSymbol ?
                            AccessType.PROPERTY : AccessType.FIELD;
                }
            }
            Symbol.ClassSymbol classSymbol = (Symbol.ClassSymbol) type;
            Type superclass = classSymbol.getSuperclass();
            type = superclass == null ? null : superclass.tsym;
        }
        return AccessType.FIELD;
    }

    static String propertyName(Symbol symbol) {
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

    static boolean isPersistable(Symbol member, AccessType accessType) {
        if (member.isStatic() || isTransient(member)) {
            return false;
        }
        else if (member instanceof Symbol.VarSymbol) {
            return accessType == AccessType.FIELD
                || hasAnnotation(member, "javax.persistence.Access");
        }
        else if (member instanceof Symbol.MethodSymbol) {
            return isGetterMethod((Symbol.MethodSymbol) member)
                && (accessType == AccessType.PROPERTY
                    || hasAnnotation(member, "javax.persistence.Access"));
        }
        else {
            return false;
        }
    }

    static Type getMemberType(Symbol member) {
        return member instanceof Symbol.MethodSymbol ?
                ((Symbol.MethodSymbol) member).getReturnType() :
                member.type;
    }
}
