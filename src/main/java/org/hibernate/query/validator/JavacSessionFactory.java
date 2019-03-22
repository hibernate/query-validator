package org.hibernate.query.validator;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.PackageSymbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Names;
import org.hibernate.persister.entity.EntityPersister;

import javax.annotation.processing.ProcessingEnvironment;
import javax.persistence.Entity;
import java.util.ArrayList;
import java.util.Collection;

class JavacSessionFactory extends MockSessionFactory {

    private final Names names;
    private final Symtab syms;

    JavacSessionFactory(ProcessingEnvironment processingEnv) {
        Context context = ((JavacProcessingEnvironment) processingEnv).getContext();
        names = Names.instance(context);
        syms = Symtab.instance(context);
    }

    static Symbol lookup(Names names, Symbol container, String name) {
        return container.members().lookup(names.fromString(name)).sym;
    }

    static Symbol superclassLookup(Names names, Symbol type, String name) {
        //iterate up the superclass hierarchy
        while (type instanceof ClassSymbol) {
            Symbol member = lookup(names, type, name);
            if (member!=null) {
                return member;
            }
            else {
                ClassSymbol classSymbol = (ClassSymbol) type;
                Type superclass = classSymbol.getSuperclass();
                type = superclass==null ? null : superclass.tsym;
            }
        }
        return null;
    }

    @Override
    EntityPersister createMockEntityPersister(String entityName) {
        //TODO: is it truly quicker to split the search up into two steps like this??
        //first search for things with defaulted entity names
        Collection<PackageSymbol> packages = new ArrayList<>(syms.packages.values());
        for (PackageSymbol pack: packages) {
            try {
                Symbol type = lookup(names, pack, entityName);
                if (type != null) {
                    Entity entity = type.getAnnotation(Entity.class);
                    if (entity != null) {
                        if (entity.name().isEmpty() || entity.name().equals(entityName)) {
                            return new JavacEntityPersister(entityName, type, names, this);
                        }
                    }
                }
            }
            catch (Exception e) {}
        }
        //search for things by explicit @Entity(name="...")
        for (PackageSymbol pack: packages) {
            try {
                for (Symbol type : pack.getEnclosedElements()) {
                    Entity entity = type.getAnnotation(Entity.class);
                    if (entity != null && entity.name().equals(entityName)) {
                        return new JavacEntityPersister(entityName, type, names, this);
                    }
                }
            }
            catch (Exception e) {}
        }
        return null;
    }

}
