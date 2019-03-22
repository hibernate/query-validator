package org.hibernate.query.validator;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.PackageSymbol;
import org.hibernate.persister.entity.EntityPersister;

import javax.lang.model.element.AnnotationMirror;

import java.util.HashMap;
import java.util.Map;

import static org.hibernate.query.validator.JavacHelper.*;

class JavacSessionFactory extends MockSessionFactory {

    private Map<String, EntityPersister> cache = new HashMap<>();

    @Override
    EntityPersister createMockEntityPersister(String entityName) {

        EntityPersister cached = cache.get(entityName);
        if (cached!=null) return cached;

        //TODO: is it truly quicker to split the search up into two steps like this??
        //first search for things with defaulted entity names
        for (PackageSymbol pack: packages()) {
            try {
                Symbol type = lookup(pack, entityName);
                if (type != null) {
                    AnnotationMirror entity = entityAnnotation(type);
                    if (entity != null) {
                        String name = entityName(entity);
                        if (name.isEmpty() || name.equals(entityName)) {
                            JavacEntityPersister persister =
                                    new JavacEntityPersister(entityName, type, this);
                            cache.put(entityName, persister);
                            return persister;
                        }
                    }
                }
            }
            catch (Exception e) {}
        }
        //search for things by explicit @Entity(name="...")
        for (PackageSymbol pack: packages()) {
            try {
                for (Symbol type: pack.members().getElements()) {
                    AnnotationMirror entity = entityAnnotation(type);
                    if (entity != null && entityName(entity).equals(entityName)) {
                        JavacEntityPersister persister =
                                new JavacEntityPersister(entityName, type, this);
                        cache.put(entityName, persister);
                        return persister;
                    }
                }
            }
            catch (Exception e) {}
        }
        return null;
    }

}
