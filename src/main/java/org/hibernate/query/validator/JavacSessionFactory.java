package org.hibernate.query.validator;

import com.sun.tools.javac.code.Symbol;
import org.hibernate.QueryException;
import org.hibernate.persister.collection.CollectionPersister;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.type.CollectionType;
import org.hibernate.type.Type;
import org.hibernate.type.TypeFactory;

import javax.lang.model.element.AnnotationMirror;
import java.util.HashMap;
import java.util.Map;

import static org.hibernate.query.validator.JavacHelper.*;
import static org.hibernate.query.validator.MockCollectionPersister.createAssociationCollection;
import static org.hibernate.query.validator.MockCollectionPersister.createElementCollection;

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
            EntityPersister persister =
                    new MockEntityPersister(entityName, this) {
                        private Map<String,Type> cache = new HashMap<>();
                        @Override
                        public Type getPropertyType(String propertyName)
                                throws QueryException {
                            Type cached = cache.get(propertyName);
                            if (cached!=null) return cached;
                            Type result = propertyType(type.type,
                                    getEntityName(), propertyName);
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
        int index = role.lastIndexOf('.');
        String entityName = role.substring(0,index);
        String propertyName = role.substring(index+1);
        Symbol property = lookup(lookupEntity(entityName), propertyName);
        AnnotationMirror toMany = toManyAnnotation(property);
        CollectionType collectionType = collectionType(property.type, role);
        com.sun.tools.javac.code.Type elementType = property.type.getTypeArguments().last();
        if (elementType==null) elementType = syms.objectType;
        if (toMany!=null) {
            String elementEntity = targetEntity(toMany);
            if (elementEntity==null) {
                elementEntity = simpleName(elementType);
            }
            return createAssociationCollection(role, collectionType, entityName, elementEntity, this);
        }
        AnnotationMirror elementCollection = elementCollectionAnnotation(property);
        if (elementCollection!=null) {
            String elementClass = targetClass(elementCollection);
            if (elementClass==null) {
                elementClass = qualifiedName(elementType);
            }
            return createElementCollection(role, collectionType, entityName, elementClass, this);
        }
        return null;
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

    private static Type propertyType(com.sun.tools.javac.code.Type type,
                             String entityName, String propertyPath) {
        com.sun.tools.javac.code.Type memberType = type;
        Type result = null;
        //iterate over the path segments
        for (String segment: propertyPath.split("\\.")) {
            Symbol member = superclassLookup(memberType.tsym, segment);
            if (member == null || hasTransientAnnotation(member)) {
                result = null;
                break;
            }
            else {
                memberType = member.type;
                AnnotationMirror toMany = toManyAnnotation(member);
                if (toMany!=null) {
                    //TODO: should trim down to current propertyPath
                    String role = entityName + '.' + propertyPath;
                    result = collectionType(memberType, role);
                    continue;
                }
                AnnotationMirror toOne = toOneAnnotation(member);
                if (toOne!=null) {
                    String targetEntity = targetEntity(toOne);
                    if (targetEntity==null) {
                        targetEntity = simpleName(member.type);
                    }
                    result = typeHelper.entity(targetEntity);
                    continue;
                }
                AnnotationMirror elementCollection =
                        elementCollectionAnnotation(member);
                if (elementCollection!=null) {
                    //TODO: should trim down to current propertyPath
                    String role = entityName + '.' + propertyPath;
                    result = collectionType(memberType, role);
                    continue;
                }
                result = typeResolver.basic(qualifiedName(memberType));
            }
        }
        return result;
    }

}
