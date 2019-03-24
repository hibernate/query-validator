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

    static Names names;
    static Types types;
    static Symtab syms;

    static void init(JavacProcessingEnvironment processingEnv) {
        Context context = processingEnv.getContext();
        names = Names.instance(context);
        types = Types.instance(context);
        syms = Symtab.instance(context);
    }

    private static Symbol findMember(Symbol container, String name) {
        return container.members().lookup(names.fromString(name)).sym;
    }

    static Symbol findProperty(Symbol.TypeSymbol type, String propertyName,
                               AccessType defaultAccessType) {
        //iterate up the superclass hierarchy
        while (type instanceof Symbol.ClassSymbol) {
            if (isMappedClass(type)) {
                AccessType accessType =
                        getAccessType(type, defaultAccessType);
                Symbol member = null;
                switch (accessType) {
                    case FIELD:
                        member = findMember(type, propertyName);
                        break;
                    case PROPERTY:
                        String capitalized =
                                propertyName.substring(0, 1).toUpperCase()
                                + propertyName.substring(1).toLowerCase();
                        member = findMember(type, "get" + capitalized);
                        if (member == null) {
                            member = findMember(type, "is" + capitalized);
                        }
                        break;
                }
                if (member != null && isPersistent(member)) {
                    return member;
                }
            }
            Symbol.ClassSymbol classSymbol = (Symbol.ClassSymbol) type;
            Type superclass = classSymbol.getSuperclass();
            type = superclass == null ? null : superclass.tsym;
        }
        return null;
    }

    static Symbol.PackageSymbol findPackage(String packageName) {
        return syms.packages.get(names.fromString(packageName));
    }

    static ArrayList<Symbol.PackageSymbol> allPackages() {
        return new ArrayList<>(syms.packages.values());
    }

    static boolean isPersistent(Symbol member) {
        if (member.isStatic() || isTransient(member)) {
            return false;
        }
        else if (member instanceof Symbol.VarSymbol) {
            return true;
        }
        else if (member instanceof Symbol.MethodSymbol) {
            Symbol.MethodSymbol method =
                    (Symbol.MethodSymbol) member;
            if (!method.params().isEmpty()) {
                return false;
            }
            String methodName = method.name.toString();
            return methodName.startsWith("get")
                || methodName.startsWith("is")
                    && member.type.getTag() == TypeTag.BOOLEAN;
        }
        else {
            return false;
        }
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

    private static boolean isMappedClass(Symbol.TypeSymbol type) {
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
        return type==null ? null : type.tsym.name.toString();
    }

    static String qualifiedName(Type type) {
        return type==null ? null : type.tsym.flatName().toString();
    }

    static Type memberType(Symbol property) {
        return property instanceof Symbol.MethodSymbol ?
                ((Symbol.MethodSymbol) property).getReturnType() :
                property.type;
    }

    static AccessType getAccessType(Symbol.TypeSymbol type, AccessType defaultAccessType) {
        AnnotationMirror annotation = getAnnotation(type, "javax.persistence.Access");
        if (annotation==null) {
            return defaultAccessType;
        }
        else {
            Symbol.VarSymbol member = (Symbol.VarSymbol)
                    getAnnotationMember(annotation, "value");
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
        String name = (String) getAnnotationMember(entityAnnotation, "name");
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

    static Symbol.ClassSymbol findEntityClass(String entityName) {
        //TODO: is it truly quicker to split the search up into two steps like this??
        //first search for things with defaulted entity names
        for (Symbol.PackageSymbol pack: allPackages()) {
            try {
                Symbol type = findMember(pack, entityName);
                if (type instanceof Symbol.ClassSymbol) {
                    Symbol.ClassSymbol current = (Symbol.ClassSymbol) type;
                    if (isEntity(current)
                            && getEntityName(current).equals(entityName)) {
                        return current;
                    }
                }
            }
            catch (Exception e) {}
        }
        //search for things by explicit @Entity(name="...")
        for (Symbol.PackageSymbol pack: allPackages()) {
            try {
                for (Symbol type: pack.members().getElements()) {
                    if (type instanceof Symbol.ClassSymbol) {
                        Symbol.ClassSymbol current = (Symbol.ClassSymbol) type;
                        if (isEntity(current)
                                && getEntityName(current).equals(entityName)) {
                            return current;
                        }
                    }
                }
            }
            catch (Exception e) {}
        }
        return null;
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
}
