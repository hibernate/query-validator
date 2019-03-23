package org.hibernate.query.validator;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Names;
import org.hibernate.type.CollectionType;
import org.hibernate.type.TypeFactory;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeKind;
import java.util.ArrayList;
import java.util.Map;

import static org.hibernate.query.validator.MockSessionFactory.typeResolver;

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

    static Symbol lookup(Symbol container, String name) {
        return container.members().lookup(names.fromString(name)).sym;
    }

    static Symbol superclassLookup(Symbol type, String name) {
        //iterate up the superclass hierarchy
        while (type instanceof Symbol.ClassSymbol) {
            Symbol member = lookup(type, name);
            if (member!=null) {
                return member;
            }
            else {
                Symbol.ClassSymbol classSymbol = (Symbol.ClassSymbol) type;
                Type superclass = classSymbol.getSuperclass();
                type = superclass==null ? null : superclass.tsym;
            }
        }
        return null;
    }

    static Symbol.PackageSymbol lookupPackage(String packageName) {
        return syms.packages.get(names.fromString(packageName));
    }

    static ArrayList<Symbol.PackageSymbol> packages() {
        return new ArrayList<>(syms.packages.values());
    }

    static boolean hasTransientAnnotation(Symbol member) {
        for (AnnotationMirror mirror : member.getAnnotationMirrors()) {
            if (qualifiedName((Type.ClassType) mirror.getAnnotationType())
                    .equals("javax.persistence.Transient")) {
                return true;
            }
        }
        return false;
    }

    static AnnotationMirror entityAnnotation(Symbol member) {
        for (AnnotationMirror mirror : member.getAnnotationMirrors()) {
            if (qualifiedName((Type.ClassType) mirror.getAnnotationType())
                    .equals("javax.persistence.Entity")) {
                return mirror;
            }
        }
        return null;
    }

    static AnnotationMirror toOneAnnotation(Symbol member) {
        for (AnnotationMirror mirror : member.getAnnotationMirrors()) {
            String name = qualifiedName((Type.ClassType) mirror.getAnnotationType());
            if (name.equals("javax.persistence.ManyToOne")
                    || name.equals("javax.persistence.OneToOne")) {
                return mirror;
            }
        }
        return null;
    }

    static AnnotationMirror toManyAnnotation(Symbol member) {
        for (AnnotationMirror mirror : member.getAnnotationMirrors()) {
            String name = qualifiedName((Type.ClassType) mirror.getAnnotationType());
            if (name.equals("javax.persistence.ManyToMany")
                    || name.equals("javax.persistence.OneToMany")) {
                return mirror;
            }
        }
        return null;
    }

    static String targetEntityName(Symbol member, AnnotationMirror mirror) {
        String targetEntity;
        switch (qualifiedName((Type.ClassType) mirror.getAnnotationType())) {
            case "javax.persistence.ManyToOne":
            case "javax.persistence.OneToOne":
                targetEntity = targetEntity(mirror);
                return targetEntity==null ?
                        simpleName(member.type) :
                        targetEntity;
            case "javax.persistence.ManyToMany":
            case "javax.persistence.OneToMany":
                targetEntity = targetEntity(mirror);
                return targetEntity==null ?
                        simpleName(member.type.getTypeArguments().head) :
                        targetEntity;
        }
        return null;
    }

    private static String simpleName(Type type) {
        return type==null ? null : type.tsym.name.toString();
    }

    static String qualifiedName(Type type) {
        return type==null ? null : type.tsym.flatName().toString();
    }

    static String entityName(AnnotationMirror mirr) {
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
                mirr.getElementValues().entrySet()) {
            if (entry.getKey().getSimpleName().toString().equals("name")) {
                return (String) entry.getValue().getValue();
            }
        }
        return "";
    }

    static String targetEntity(AnnotationMirror mirr) {
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
                mirr.getElementValues().entrySet()) {
            if (entry.getKey().getSimpleName().toString().equals("targetEntity")) {
                Type.ClassType classType = (Type.ClassType) entry.getValue().getValue();
                return classType.getKind() == TypeKind.VOID ? null :
                        classType.tsym.name.toString();
            }
        }
        return null;
    }

    static Symbol.ClassSymbol lookupEntity(String entityName) {
        //TODO: is it truly quicker to split the search up into two steps like this??
        //first search for things with defaulted entity names
        for (Symbol.PackageSymbol pack: packages()) {
            try {
                Symbol type = lookup(pack, entityName);
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
        for (Symbol.PackageSymbol pack: packages()) {
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

    static Symbol.ClassSymbol lookupQualifiedClass(String path) {
        return syms.classes.get(names.fromString(path));
    }

    static boolean isAssignable(Symbol.VarSymbol param, Symbol.ClassSymbol typeClass) {
        return !typeClass.isSubClass(param.type.tsym, types);
    }

}
