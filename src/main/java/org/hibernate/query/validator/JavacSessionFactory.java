package org.hibernate.query.validator;

import com.sun.tools.javac.code.Symbol;
import org.hibernate.persister.entity.EntityPersister;

import java.util.HashMap;
import java.util.Map;

import static org.hibernate.query.validator.JavacHelper.lookupEntity;

class JavacSessionFactory extends MockSessionFactory {

    private Map<String, EntityPersister> cache = new HashMap<>();

    @Override
    EntityPersister createMockEntityPersister(String entityName) {
        EntityPersister cached = cache.get(entityName);
        if (cached!=null) return cached;
        Symbol.ClassSymbol type = lookupEntity(entityName);
        if (type==null) {
            return null;
        }
        else {
            JavacEntityPersister persister =
                    new JavacEntityPersister(entityName, type, this);
            cache.put(entityName, persister);
            return persister;
        }
    }

}
