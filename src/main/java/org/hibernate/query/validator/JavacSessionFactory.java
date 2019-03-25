package org.hibernate.query.validator;

import com.sun.tools.javac.code.Symbol;
import org.hibernate.QueryException;
import org.hibernate.persister.collection.CollectionPersister;
import org.hibernate.type.*;

import javax.persistence.AccessType;
import java.util.*;

import static java.util.Arrays.stream;
import static org.hibernate.internal.util.StringHelper.*;
import static org.hibernate.query.validator.JavacHelper.*;

class JavacSessionFactory extends MockSessionFactory {

    private static final CustomType UNKNOWN_TYPE = new CustomType(new MockUserType());

    private final Map<String,EntityPersister> entityPersisters = new HashMap<>();
    private final Map<String,CollectionPersister> collectionPersisters = new HashMap<>();

    JavacSessionFactory(boolean strict) {
        super(strict);
    }

    @Override
    EntityPersister createMockEntityPersister(String entityName) {
        EntityPersister cached = entityPersisters.get(entityName);
        if (cached!=null) return cached;

        EntityPersister persister;
        Symbol.ClassSymbol type = findEntityClass(entityName);
        if (type==null) {
            return null;
        }
        else {
            persister = new EntityPersister(entityName, type);
        }

        entityPersisters.put(entityName, persister);
        return persister;
    }

    @Override
    CollectionPersister createMockCollectionPersister(String role) {
        CollectionPersister cached = collectionPersisters.get(role);
        if (cached!=null) return cached;

        CollectionPersister persister;
        String entityName = root(role); //only works because entity names don't contain dots
        String propertyPath = unroot(role);
        Symbol.ClassSymbol entityClass = findEntityClass(entityName);
        AccessType defaultAccessType = getDefaultAccessType(entityClass);
        Symbol property =
                findPropertyByPath(entityClass, propertyPath, defaultAccessType);
        CollectionType collectionType = collectionType(memberType(property), role);
        if (isToManyAssociation(property)) {
            persister = new ToManyAssociationPersister(role, collectionType,
                    getToManyTargetEntityName(property));
        }
        else if (isElementCollectionProperty(property)) {
            Symbol.TypeSymbol elementType =
                    getElementCollectionElementType(property).tsym;
            persister = new ElementCollectionPersister(role, collectionType,
                    elementType, propertyPath, defaultAccessType);
        }
        else {
            return null;
        }

        collectionPersisters.put(role, persister);
        return persister;
    }

    private static Symbol findPropertyByPath(Symbol.TypeSymbol type,
                                             String propertyPath,
                                             AccessType defaultAccessType) {
        return stream(split(".", propertyPath))
                .reduce((Symbol) type,
                        (symbol, segment) -> symbol==null ? null :
                                findProperty(getMemberType(symbol).tsym,
                                        segment, defaultAccessType),
                        (last, current) -> current);
    }

    static Type propertyType(Symbol member,
                             String entityName, String path,
                             AccessType defaultAccessType) {
        com.sun.tools.javac.code.Type memberType = getMemberType(member);
        if (isEmbeddedProperty(member)) {
            return new CompositeCustomType(
                    new Component(memberType.tsym,
                            entityName, path, defaultAccessType)) {
                @Override
                public String getName() {
                    return simpleName(memberType);
                }
            };
        }
        else if (isToOneAssociation(member)) {
            String targetEntity = getToOneTargetEntity(member);
            return typeHelper.entity(targetEntity);
        }
        else if (isToManyAssociation(member)) {
            return collectionType(memberType, qualify(entityName, path));
        }
        else if (isElementCollectionProperty(member)) {
            return collectionType(memberType, qualify(entityName,path));
        }
        else {
            Type result = typeResolver.basic(qualifiedName(memberType));
            return result == null ? UNKNOWN_TYPE : result;
        }
    }

    private static Type elementCollectionElementType(Symbol.TypeSymbol elementType,
                                                     String role, String path,
                                                     AccessType defaultAccessType) {
        if (isEmbeddableType(elementType)) {
            return new CompositeCustomType(
                    new Component(elementType,
                            role, path, defaultAccessType)) {
                @Override
                public String getName() {
                    return simpleName(elementType.type);
                }
            };
        }
        else {
            return typeResolver.basic(qualifiedName(elementType.type));
        }
    }

    private static CollectionType collectionType(
            com.sun.tools.javac.code.Type type, String role) {
        @SuppressWarnings("deprecation")
        TypeFactory typeFactory = typeResolver.getTypeFactory();
        switch (simpleName(type)) {
            case "Set":
            case "SortedSet":
                //might actually be a bag!
                //TODO: look for @OrderColumn on the property
                return typeFactory.set(role, null);
            case "List":
            case "SortedList":
                return typeFactory.list(role, null);
            case "Map":
            case "SortedMap":
                return typeFactory.map(role, null);
            default:
                return typeFactory.bag(role, null);
        }
    }

    private static class Component extends MockComponent {
        private String[] propertyNames;
        private Type[] propertyTypes;

        Component(Symbol.TypeSymbol type,
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
                                propertyType(member, entityName,
                                        qualify(path, name),
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

    private class EntityPersister extends MockEntityPersister {
        private final Map<String,Type> properties = new HashMap<>();
        private final List<EntityPersister> subclassPersisters = new ArrayList<>();
        private final Symbol.ClassSymbol type;
        private final AccessType defaultAccessType;

        private EntityPersister(String entityName, Symbol.ClassSymbol type) {
            super(entityName, JavacSessionFactory.this);
            this.type = type;
            defaultAccessType = getDefaultAccessType(type);
            for (EntityPersister other: entityPersisters.values()) {
                other.addPersister(this);
                this.addPersister(other);
            }
        }

        void addPersister(EntityPersister entityPersister) {
            if (isSubclass(entityPersister.type,type)) {
                subclassPersisters.add(entityPersister);
            }
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
                    propertyType(symbol, getEntityName(),
                            propertyPath, defaultAccessType);

            if (result == null) {
                //check subclasses, needed for treat()
                result = subclassPersisters.stream()
                        .map(sp -> sp.getPropertyType(propertyPath))
                        .filter(Objects::nonNull)
                        .findAny()
                        .orElse(null);
            }

            properties.put(propertyPath, result);
            return result;
        }

    }

    private class ToManyAssociationPersister extends MockCollectionPersister {
        ToManyAssociationPersister(String role,
                                   CollectionType collectionType,
                                   String targetEntityName) {
            super(role, collectionType,
                    typeHelper.entity(targetEntityName),
                    JavacSessionFactory.this);
        }

        @Override
        Type getElementPropertyType(String propertyPath) {
            return getElementPersister().getPropertyType(propertyPath);
        }
    }

    private class ElementCollectionPersister extends MockCollectionPersister {
        private final Symbol.TypeSymbol elementType;
        private final AccessType defaultAccessType;

        ElementCollectionPersister(String role,
                                   CollectionType collectionType,
                                   Symbol.TypeSymbol elementType,
                                   String propertyPath,
                                   AccessType defaultAccessType) {
            super(role, collectionType,
                    elementCollectionElementType(elementType, role,
                            propertyPath, defaultAccessType),
                    JavacSessionFactory.this);
            this.elementType = elementType;
            this.defaultAccessType = defaultAccessType;
        }

        @Override
        Type getElementPropertyType(String propertyPath) {
            Symbol symbol =
                    findPropertyByPath(elementType, propertyPath,
                            defaultAccessType);
            return symbol == null ? null :
                    propertyType(symbol, getOwnerEntityName(),
                            propertyPath, defaultAccessType);
        }
    }
}
