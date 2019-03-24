package org.hibernate.query.validator;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Names;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeKind;
import javax.persistence.AccessType;
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

    static Symbol findProperty(Symbol.TypeSymbol type, String propertyName) {
        Symbol current = type;
        //iterate up the superclass hierarchy
        while (current instanceof Symbol.ClassSymbol) {
            Symbol member = findMember(current, propertyName);
            String capitalized =
                    propertyName.substring(0, 1).toUpperCase()
                    + propertyName.substring(1).toLowerCase();
            if (member==null) {
                member = findMember(current, "get" + capitalized);
            }
            if (member==null) {
                member = findMember(current, "is" + capitalized);
            }
            if (member!=null && isPersistent(member)) {
                return member;
            }
            else {
                Symbol.ClassSymbol classSymbol = (Symbol.ClassSymbol) current;
                Type superclass = classSymbol.getSuperclass();
                current = superclass == null ? null : superclass.tsym;
            }
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
        if (hasTransientAnnotation(member) || member.isStatic()) {
            return false;
        }
        else if (member instanceof Symbol.VarSymbol) {
            return true;
        }
        else if (member instanceof Symbol.MethodSymbol) {
            Symbol.MethodSymbol method = (Symbol.MethodSymbol) member;
            if (!method.params().isEmpty()) {
                return false;
            }
            String methodName = method.name.toString();
            return methodName.startsWith("get")
                || methodName.startsWith("is");
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

    static boolean hasTransientAnnotation(Symbol member) {
        return hasAnnotation(member, "javax.persistence.Transient");
    }

    static boolean hasEmbedAnnotation(Symbol member) {
        return hasAnnotation(member, "javax.persistence.Embedded")
            || hasAnnotation(member.type.tsym, "javax.persistence.Embeddable");
    }

    static AnnotationMirror entityAnnotation(Symbol member) {
        return getAnnotation(member, "javax.persistence.Entity");
    }

    static AnnotationMirror elementCollectionAnnotation(Symbol member) {
        return getAnnotation(member, "javax.persistence.ElementCollection");
    }

    static AnnotationMirror toOneAnnotation(Symbol member) {
        AnnotationMirror manyToOne =
                getAnnotation(member, "javax.persistence.ManyToOne");
        if (manyToOne!=null) return manyToOne;
        AnnotationMirror oneToOne =
                getAnnotation(member, "javax.persistence.OneToOne");
        if (oneToOne!=null) return oneToOne;
        return null;
    }

    static AnnotationMirror toManyAnnotation(Symbol member) {
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

    static String entityName(AnnotationMirror annotation) {
        String name = (String) getAnnotationMember(annotation, "name");
        return name==null ? "" : name;
    }

    static String targetEntity(AnnotationMirror annotation) {
        Type.ClassType classType = (Type.ClassType)
                getAnnotationMember(annotation, "targetEntity");
        return classType==null || classType.getKind() == TypeKind.VOID ?
                null : classType.tsym.name.toString();
    }

    static String targetClass(AnnotationMirror annotation) {
        Type.ClassType classType = (Type.ClassType)
                getAnnotationMember(annotation, "targetClass");
        return classType==null || classType.getKind() == TypeKind.VOID ?
                null : classType.tsym.flatName().toString();
    }

    static Symbol.ClassSymbol findEntityClass(String entityName) {
        //TODO: is it truly quicker to split the search up into two steps like this??
        //first search for things with defaulted entity names
        for (Symbol.PackageSymbol pack: allPackages()) {
            try {
                Symbol type = findMember(pack, entityName);
                if (type instanceof Symbol.ClassSymbol) {
                    AnnotationMirror entity = entityAnnotation(type);
                    if (entity != null) {
                        String name = entityName(entity);
                        if (name.isEmpty() || name.equals(entityName)) {
                            return (Symbol.ClassSymbol) type;
                        }
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
                        AnnotationMirror entity = entityAnnotation(type);
                        if (entity != null && entityName(entity).equals(entityName)) {
                            return (Symbol.ClassSymbol) type;
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

}
