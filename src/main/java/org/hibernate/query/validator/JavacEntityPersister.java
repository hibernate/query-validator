package org.hibernate.query.validator;

import com.sun.tools.javac.code.Symbol;
import org.hibernate.QueryException;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.type.StandardBasicTypes;
import org.hibernate.type.Type;

import java.util.HashMap;
import java.util.Map;

import static org.hibernate.query.validator.JavacHelper.*;
import static org.hibernate.query.validator.MockSessionFactory.typeHelper;
import static org.hibernate.query.validator.MockSessionFactory.typeResolver;

class JavacEntityPersister extends MockEntityPersister {

    private final Symbol type;

    private Map<String,Type> cache = new HashMap<>();

    JavacEntityPersister(String entityName, Symbol type,
                         MockSessionFactory factory) {
        super(entityName, factory);
        this.type = type;
    }

    @Override
    public Type getIdentifierType() {
        //TODO: getPropertyType(getIdentifierPropertyName())
        return StandardBasicTypes.INTEGER;
    }

    @Override
    public String getIdentifierPropertyName() {
        //TODO!!!!!!
        return "id";
    }

    @Override
    public Type getPropertyType(String propertyName)
            throws QueryException {

        Type cached = cache.get(propertyName);
        if (cached!=null) return cached;

        com.sun.tools.javac.code.Type memberType = type.type;
        String memberEntityName = null;
        //iterate over the path segments
        for (String segment: propertyName.split("\\.")) {
            Symbol member = superclassLookup(memberType.tsym, segment);
            if (member == null || hasTransientAnnotation(member)) {
                return null;
            }
            else {
                memberType = member.type;
                memberEntityName = targetEntityName(member);
            }
        }
        Type type = memberEntityName != null ?
                typeHelper.entity(memberEntityName) :
                typeResolver.basic(qualifiedName(memberType));
        cache.put(propertyName, type);
        return type;
    }

}
