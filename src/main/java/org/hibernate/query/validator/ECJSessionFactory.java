package org.hibernate.query.validator;

import org.eclipse.jdt.internal.compiler.ast.TypeDeclaration;
import org.eclipse.jdt.internal.compiler.lookup.*;
import org.hibernate.QueryException;
import org.hibernate.hql.internal.ast.ParseErrorHandler;
import org.hibernate.type.CollectionType;
import org.hibernate.type.CompositeCustomType;
import org.hibernate.type.Type;

import javax.persistence.AccessType;
import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.stream;
import static org.hibernate.internal.util.StringHelper.*;
import static org.hibernate.query.validator.ECJHelper.*;

class ECJSessionFactory extends MockSessionFactory {

    ECJSessionFactory(ParseErrorHandler handler) {
        super(handler);
    }

    @Override
    MockEntityPersister createMockEntityPersister(String entityName) {
        MockEntityPersister cached = entityPersisters.get(entityName);
        if (cached!=null) return cached;

        EntityPersister persister;
        TypeDeclaration type = findEntityClass(entityName);
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
    MockCollectionPersister createMockCollectionPersister(String role) {
        MockCollectionPersister cached = collectionPersisters.get(role);
        if (cached!=null) return cached;

        MockCollectionPersister persister;
        String entityName = root(role); //only works because entity names don't contain dots
        String propertyPath = unroot(role);
        TypeBinding entityClass = findEntityClass(entityName).binding;
        AccessType defaultAccessType = getDefaultAccessType(entityClass);
        Binding property =
                findPropertyByPath(entityClass, propertyPath, defaultAccessType);
        CollectionType collectionType = collectionType(getMemberType(property), role);
        if (isToManyAssociation(property)) {
            persister = new ToManyAssociationPersister(role, collectionType,
                    getToManyTargetEntityName(property));
        }
        else if (isElementCollectionProperty(property)) {
            TypeBinding elementType =
                    getElementCollectionElementType(property);
            persister = new ElementCollectionPersister(role, collectionType,
                    elementType, propertyPath, defaultAccessType);
        }
        else {
            return null;
        }

        collectionPersisters.put(role, persister);
        return persister;
    }

    private static Binding findPropertyByPath(TypeBinding type,
                                             String propertyPath,
                                             AccessType defaultAccessType) {
        return stream(split(".", propertyPath))
                .reduce((Binding) type,
                        (symbol, segment) -> symbol==null ? null :
                                findProperty(getMemberType(symbol),
                                        segment, defaultAccessType),
                        (last, current) -> current);
    }

    static Type propertyType(Binding member,
                             String entityName, String path,
                             AccessType defaultAccessType) {
        TypeBinding memberType = getMemberType(member);
        if (isEmbeddedProperty(member)) {
            return new CompositeCustomType(
                    new Component(memberType,
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

    private static Type elementCollectionElementType(TypeBinding elementType,
                                                     String role, String path,
                                                     AccessType defaultAccessType) {
        if (isEmbeddableType(elementType)) {
            return new CompositeCustomType(
                    new Component(elementType,
                            role, path, defaultAccessType)) {
                @Override
                public String getName() {
                    return simpleName(elementType);
                }
            };
        }
        else {
            return typeResolver.basic(qualifiedName(elementType));
        }
    }

    private static CollectionType collectionType(
            TypeBinding type, String role) {
        return createCollectionType(role, simpleName(type.actualType()));
    }

    private static class Component extends MockComponent {
        private String[] propertyNames;
        private Type[] propertyTypes;

        Component(TypeBinding type,
                  String entityName, String path,
                  AccessType defaultAccessType) {
            List<String> names = new ArrayList<>();
            List<Type> types = new ArrayList<>();

            while (type instanceof SourceTypeBinding) {
                SourceTypeBinding classSymbol =
                        (SourceTypeBinding) type;
                if (isMappedClass(type)) { //ignore unmapped intervening classes
                    AccessType accessType =
                            getAccessType(type, defaultAccessType);
                    for (MethodBinding member: classSymbol.methods()) {
                        if (isPersistable(member, accessType)) {
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
                    for (FieldBinding member: classSymbol.fields()) {
                        if (isPersistable(member, accessType)) {
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
                }
                type = classSymbol.superclass;
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
        private final TypeDeclaration type;

        private EntityPersister(String entityName, TypeDeclaration type) {
            super(entityName, getDefaultAccessType(type.binding),
                    ECJSessionFactory.this);
            this.type = type;
            initSubclassPersisters();
        }

        @Override
        boolean isSubclassPersister(MockEntityPersister entityPersister) {
            EntityPersister persister = (EntityPersister) entityPersister;
            return isSubclass(persister.type.binding, type.binding);
        }

        @Override
        public Type getPropertyType(String propertyPath)
                throws QueryException {
            Type cached = properties.get(propertyPath);
            if (cached!=null) return cached;

            Binding symbol =
                    findPropertyByPath(type.binding, propertyPath,
                            defaultAccessType);
            Type result = symbol == null ? null :
                    propertyType(symbol, getEntityName(),
                            propertyPath, defaultAccessType);

            if (result == null) {
                //check subclasses, needed for treat()
                result = getSubclassPropertyType(propertyPath);
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
                    ECJSessionFactory.this);
        }

        @Override
        Type getElementPropertyType(String propertyPath) {
            return getElementPersister().getPropertyType(propertyPath);
        }
    }

    private class ElementCollectionPersister extends MockCollectionPersister {
        private final TypeBinding elementType;
        private final AccessType defaultAccessType;

        ElementCollectionPersister(String role,
                                   CollectionType collectionType,
                                   TypeBinding elementType,
                                   String propertyPath,
                                   AccessType defaultAccessType) {
            super(role, collectionType,
                    elementCollectionElementType(elementType, role,
                            propertyPath, defaultAccessType),
                    ECJSessionFactory.this);
            this.elementType = elementType;
            this.defaultAccessType = defaultAccessType;
        }

        @Override
        Type getElementPropertyType(String propertyPath) {
            Binding symbol =
                    findPropertyByPath(elementType, propertyPath,
                            defaultAccessType);
            return symbol == null ? null :
                    propertyType(symbol, getOwnerEntityName(),
                            propertyPath, defaultAccessType);
        }
    }
}
