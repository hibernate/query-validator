package org.hibernate.query.validator;

import com.sun.tools.javac.code.Symbol;
import org.hibernate.QueryException;
import org.hibernate.persister.collection.CollectionPersister;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.type.*;

import javax.persistence.AccessType;
import java.util.HashMap;
import java.util.Map;

import static org.hibernate.query.validator.JavacHelper.*;
import static org.hibernate.query.validator.MockCollectionPersister.createAssociationCollection;
import static org.hibernate.query.validator.MockCollectionPersister.createElementCollection;

class JavacSessionFactory extends MockSessionFactory {

    private static final CustomType UNKNOWN_TYPE = new CustomType(new MockUserType());

    private Map<String, EntityPersister> cache = new HashMap<>();
    private Map<String, CollectionPersister> collcache = new HashMap<>();

    @Override
    EntityPersister createMockEntityPersister(String entityName) {
        EntityPersister cached = cache.get(entityName);
        if (cached!=null) return cached;
        Symbol.ClassSymbol type = findEntityClass(entityName);
        if (type==null) {
            return null;
        }
        else {
            EntityPersister persister =
                    new MockEntityPersister(entityName, this) {
                        private Map<String,Type> cache = new HashMap<>();
                        AccessType defaultAccessType = getDefaultAccessType(type);
                        @Override
                        public Type getPropertyType(String propertyName)
                                throws QueryException {
                            Type cached = cache.get(propertyName);
                            if (cached!=null) return cached;
                            Type result = propertyType(type.type,
                                    getEntityName(), propertyName,
                                    defaultAccessType);
                            cache.put(propertyName, result);
                            return result;
                        }

                    };
            cache.put(entityName, persister);
            return persister;
        }
    }

    @Override
    CollectionPersister createMockCollectionPersister(String role) {
        CollectionPersister cached = collcache.get(role);
        if (cached!=null) return cached;
        int index = role.lastIndexOf('.');
        String entityName = role.substring(0,index);
        String propertyName = role.substring(index+1);
        Symbol.ClassSymbol entityClass = findEntityClass(entityName);
        Symbol property = findProperty(entityClass, propertyName,
                getDefaultAccessType(entityClass));
        CollectionPersister persister = null;
        if (isToManyAssociation(property)) {
            persister = createAssociationCollection(role,
                    collectionType(memberType(property), role), entityName,
                    getToManyTargetEntity(property), this);
        }
        if (isElementCollectionProperty(property)) {
            persister = createElementCollection(role,
                    collectionType(memberType(property), role), entityName,
                    getElementCollectionClass(property), this);
        }
        collcache.put(role, persister);
        return persister;
    }

    private static CollectionType collectionType(com.sun.tools.javac.code.Type type, String role) {
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

    static Type propertyType(com.sun.tools.javac.code.Type type,
                             String entityName, String propertyPath,
                             AccessType defaultAccessType) {
        return propertyType(type, entityName, propertyPath, null,
                defaultAccessType);
    }
    static Type propertyType(com.sun.tools.javac.code.Type type,
                             String entityName, String propertyPath,
                             String prefix, AccessType defaultAccessType) {
        com.sun.tools.javac.code.Type memberType = type;
        Type result = null;
        //iterate over the path segments
        StringBuilder currentPath = new StringBuilder();
        for (String segment: propertyPath.split("\\.")) {
            currentPath.append(segment);
            Symbol member =
                    findProperty(memberType.tsym, segment,
                            defaultAccessType);
            if (member == null) {
                result = null;
                break;
            }
            else {
                memberType = member instanceof Symbol.MethodSymbol ?
                        ((Symbol.MethodSymbol) member).getReturnType() :
                        member.type;
                if (isEmbeddedProperty(member)) {
                    String path = currentPath.toString();
                    if (prefix!=null) path = prefix + '.' + path;
                    result = new CompositeCustomType(
                            new JavacComponent(member.type, entityName, path,
                                    defaultAccessType));
                    continue;
                }

                if (isToManyAssociation(member)) {
                    String role = entityName + '.' + currentPath;
                    if (prefix!=null) role = prefix + '.' + role;
                    result = collectionType(memberType, role);
                    continue;
                }
                if (isToOneAssociation(member)) {
                    String targetEntity = getToOneTargetEntity(member);
                    result = typeHelper.entity(targetEntity);
                    continue;
                }
                if (isElementCollectionProperty(member)) {
                    String role = entityName + '.' + currentPath;
                    if (prefix!=null) role = prefix + '.' + role;
                    result = collectionType(memberType, role);
                    continue;
                }
                result = typeResolver.basic(qualifiedName(memberType));
                if (result == null) {
                    result = UNKNOWN_TYPE;
                }
            }
        }
        return result;
    }

}
