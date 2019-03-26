package org.hibernate.query.validator;

import org.eclipse.jdt.internal.compiler.Compiler;
import org.eclipse.jdt.internal.compiler.apt.dispatch.BaseProcessingEnvImpl;
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration;
import org.eclipse.jdt.internal.compiler.ast.TypeDeclaration;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;
import org.eclipse.jdt.internal.compiler.lookup.*;

import javax.persistence.AccessType;
import java.beans.Introspector;

import static org.eclipse.jdt.core.compiler.CharOperation.charToString;

class ECJHelper {
    static Compiler compiler;

    static void initialize(BaseProcessingEnvImpl processingEnv) {
        compiler = processingEnv.getCompiler();
    }

    static String simpleName(TypeBinding type) {
        return charToString(type.sourceName());
    }

    static String simpleName(MethodBinding binding) {
        return charToString(binding.selector);
    }

    static String simpleName(VariableBinding binding) {
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
            if (qualifiedName(ann.getAnnotationType())
                    .equals(name)) {
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

    static boolean isSubclass(TypeBinding subtype, TypeBinding type) {
        return subtype.isSubtypeOf(type);
    }

    static AccessType getDefaultAccessType(TypeBinding type) {
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

    static TypeDeclaration findEntityClass(String entityName) {
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

    static Binding findProperty(TypeBinding type, String propertyName,
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

    static String propertyName(Binding symbol) {
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

    static boolean isPersistable(Binding member, AccessType accessType) {
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

    static TypeBinding getMemberType(Binding binding) {
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

    static boolean isStatic(Binding member) {
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

    static boolean isTransient(Binding member) {
        if (member instanceof FieldBinding) {
            if ((((FieldBinding) member).modifiers & ClassFileConstants.AccTransient) != 0) {
                return true;
            }
        }
        return hasAnnotation(member, "javax.persistence.Transient");
    }

    static boolean isEmbeddableType(TypeBinding type) {
        return hasAnnotation(type, "javax.persistence.Embeddable");
    }

    static boolean isEmbeddedProperty(Binding member) {
        return hasAnnotation(member, "javax.persistence.Embedded")
                || hasAnnotation(getMemberType(member), "javax.persistence.Embeddable");
    }

    static boolean isElementCollectionProperty(Binding member) {
        return hasAnnotation(member, "javax.persistence.ElementCollection");
    }

    static boolean isToOneAssociation(Binding member) {
        return hasAnnotation(member, "javax.persistence.ManyToOne")
                || hasAnnotation(member, "javax.persistence.OneToOne");
    }

    static boolean isToManyAssociation(Binding member) {
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

    static String getToManyTargetEntityName(Binding property) {
        AnnotationBinding annotation = toManyAnnotation(property);
        TypeBinding classType = (TypeBinding)
                getAnnotationMember(annotation, "targetEntity");
        return classType==null || classType.id == TypeIds.T_void ?
                //entity names are unqualified class names
                simpleName(getCollectionElementType(property)) :
                simpleName(classType);
    }

    static TypeBinding getElementCollectionElementType(Binding property) {
        AnnotationBinding annotation = getAnnotation(property,
                "javax.persistence.ElementCollection");
        TypeBinding classType = (TypeBinding)
                getAnnotationMember(annotation, "getElementCollectionClass");
        return classType == null || classType.id == TypeIds.T_void ?
                getCollectionElementType(property) :
                classType;
    }

    static boolean isMappedClass(TypeBinding type) {
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

    static String getEntityName(TypeBinding type) {
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

    static AccessType getAccessType(TypeBinding type,
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
}