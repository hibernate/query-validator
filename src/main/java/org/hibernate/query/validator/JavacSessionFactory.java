package org.hibernate.query.validator;

import com.sun.tools.javac.code.Symbol;
import org.hibernate.QueryException;
import org.hibernate.persister.collection.CollectionPersister;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.type.*;

import javax.persistence.AccessType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hibernate.query.validator.JavacHelper.*;
import static org.hibernate.query.validator.MockCollectionPersister.createAssociationCollection;
import static org.hibernate.query.validator.MockCollectionPersister.createElementCollection;

class JavacSessionFactory extends MockSessionFactory {

    private static final CustomType UNKNOWN_TYPE = new CustomType(new MockUserType());

    private final Map<String,EntityPersister> entityPersisters = new HashMap<>();
    private final Map<String,CollectionPersister> collectionPersisters = new HashMap<>();

    @Override
    EntityPersister createMockEntityPersister(String entityName) {
        EntityPersister cached = entityPersisters.get(entityName);
        if (cached!=null) return cached;

        EntityPersister persister;
        Symbol.ClassSymbol type = findEntityClass(entityName);
        if (type==null) {
            persister = null;
        }
        else {
            persister = new JavacEntityPersister(entityName, type);
        }

        entityPersisters.put(entityName, persister);
        return persister;
    }

    @Override
    CollectionPersister createMockCollectionPersister(String role) {
        CollectionPersister cached = collectionPersisters.get(role);
        if (cached!=null) return cached;

        CollectionPersister persister;
        int index = role.lastIndexOf('.');
        String entityName = role.substring(0,index);
        String propertyPath = role.substring(index+1);
        Symbol.ClassSymbol entityClass = findEntityClass(entityName);
        Symbol property =
                findPropertyByPath(entityClass, propertyPath,
                        getDefaultAccessType(entityClass));
        if (isToManyAssociation(property)) {
            persister = createAssociationCollection(role,
                    collectionType(memberType(property), role), entityName,
                    getToManyTargetEntity(property), this);
        }
        else if (isElementCollectionProperty(property)) {
            persister = createElementCollection(role,
                    collectionType(memberType(property), role), entityName,
                    getElementCollectionClass(property), this);
        }
        else {
            persister = null;
        }

        collectionPersisters.put(role, persister);
        return persister;
    }

    static Symbol findPropertyByPath(Symbol.TypeSymbol type,
                                     String propertyPath,
                                     AccessType defaultAccessType) {
        com.sun.tools.javac.code.Type memberType = type.type;
        Symbol result = null;
        //iterate over the path segments
        for (String segment: propertyPath.split("\\.")) {
            Symbol member =
                    findProperty(memberType.tsym, segment,
                            defaultAccessType);
            if (member == null) {
                return null;
            }
            else {
                memberType = getMemberType(member);
                result = member;
            }
        }
        return result;
    }

    static Type createPropertyType(Symbol member,
                                   String entityName, String path,
                                   AccessType defaultAccessType) {
        com.sun.tools.javac.code.Type memberType = getMemberType(member);
        if (isEmbeddedProperty(member)) {
            return new CompositeCustomType(
                    new JavacComponent(memberType.tsym,
                            entityName, path, defaultAccessType));
        }
        else if (isToOneAssociation(member)) {
            String targetEntity = getToOneTargetEntity(member);
            return typeHelper.entity(targetEntity);
        }
        else if (isToManyAssociation(member)) {
            String role = entityName + '.' + path;
            return collectionType(memberType, role);
        }
        else if (isElementCollectionProperty(member)) {
            String role = entityName + '.' + path;
            return collectionType(memberType, role);
        }
        else {
            Type result = typeResolver.basic(qualifiedName(memberType));
            return result == null ? UNKNOWN_TYPE : result;
        }
    }

    private static CollectionType collectionType(
            com.sun.tools.javac.code.Type type, String role) {
        TypeFactory typeFactory = typeResolver.getTypeFactory();
        switch (type.tsym.name.toString()) {
            case "Set":
                return typeFactory.set(role, null);
            case "List":
                return typeFactory.list(role, null);
            case "Map":
                return typeFactory.map(role, null);
            default:
                return typeFactory.bag(role, null);
        }
    }

    private static class JavacComponent extends MockComponent {
        private String[] propertyNames;
        private Type[] propertyTypes;

        JavacComponent(Symbol.TypeSymbol type,
                       String entityName, String path,
                       AccessType defaultAccessType) {
            List<String> names = new ArrayList<>();
            List<Type> types = new ArrayList<>();

            while (type instanceof Symbol.ClassSymbol) {
                if (isMappedClass(type)) { //ignore unmapped intervening classes
                    AccessType accessType =
                            getAccessType(type, defaultAccessType);
                    for (Symbol member: type.members()
                            .getElements(symbol
                                    -> isPersistable(symbol, accessType))) {
                        String name = propertyName(member);
                        Type propertyType =
                                createPropertyType(member, entityName, path,
                                        defaultAccessType);
                        if (propertyType != null) {
                            names.add(name);
                            types.add(propertyType);
                        }
                    }
                }
                Symbol.ClassSymbol classSymbol =
                        (Symbol.ClassSymbol) type;
                com.sun.tools.javac.code.Type superclass =
                        classSymbol.getSuperclass();
                type = superclass == null ? null : superclass.tsym;
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

    private class JavacEntityPersister extends MockEntityPersister {
        private final Map<String,Type> properties = new HashMap<>();
        private final Symbol.ClassSymbol type;
        private final AccessType defaultAccessType;

        private JavacEntityPersister(String entityName, Symbol.ClassSymbol type) {
            super(entityName, JavacSessionFactory.this);
            this.type = type;
            defaultAccessType = getDefaultAccessType(type);
        }

        @Override
        public Type getPropertyType(String propertyPath)
                throws QueryException {
            Type cached = properties.get(propertyPath);
            if (cached!=null) return cached;

            Symbol symbol =
                    findPropertyByPath(type, propertyPath,
                            defaultAccessType);
            Type result = symbol == null ? null :
                    createPropertyType(symbol, getEntityName(),
                            propertyPath, defaultAccessType);

            properties.put(propertyPath, result);
            return result;
        }

    }
}
