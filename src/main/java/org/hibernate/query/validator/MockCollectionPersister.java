package org.hibernate.query.validator;

import org.hibernate.FetchMode;
import org.hibernate.QueryException;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.persister.collection.CollectionPersister;
import org.hibernate.persister.collection.QueryableCollection;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.type.*;

import java.io.Serializable;

import static org.hibernate.internal.util.StringHelper.root;

/**
 * @author Gavin King
 */
public abstract class MockCollectionPersister implements QueryableCollection {

    private static final String[] ID_COLUMN = {"id"};
    private static final String[] INDEX_COLUMN = {"pos"};

    private String role;
    private SessionFactoryImplementor factory;
    private CollectionType collectionType;
    private String ownerEntityName;
    private Type elementType;

    public MockCollectionPersister(String role, CollectionType collectionType,
                            Type elementType,
                            SessionFactoryImplementor factory) {
        this.role = role;
        this.collectionType = collectionType;
        this.elementType = elementType;
        this.factory = factory;
        this.ownerEntityName = root(role);
    }

    String getOwnerEntityName() {
        return ownerEntityName;
    }

    @Override
    public String getRole() {
        return role;
    }

    @Override
    public String getName() {
        return role;
    }

    @Override
    public Type getType() {
        return getElementType();
    }

    @Override
    public CollectionType getCollectionType() {
        return collectionType;
    }

    @Override
    public EntityPersister getOwnerEntityPersister() {
        return factory.getMetamodel().entityPersister(ownerEntityName);
    }

    abstract Type getElementPropertyType(String propertyPath);

    @Override
    public Type toType(String propertyName) throws QueryException {
        if (propertyName.equals("index")) {
            //this is what AbstractCollectionPersister does!
            //TODO: move it to FromElementType:626 or all
            //      the way to CollectionPropertyMapping
            return getIndexType();
        }
        Type type = getElementPropertyType(propertyName);
        if (type==null) {
            throw new QueryException(elementType.getName()
                    + " has no mapped "
                    + propertyName);
        }
        else {
            return type;
        }
    }

    @Override
    public Type getKeyType() {
        return getOwnerEntityPersister().getIdentifierType();
    }

    @Override
    public Type getIndexType() {
        if (collectionType instanceof ListType) {
            return IntegerType.INSTANCE;
        }
        else if (collectionType instanceof MapType) {
            return StringType.INSTANCE; //TODO!!!
        }
        else {
            return null;
        }
    }

    @Override
    public Type getElementType() {
        return elementType;
    }

    @Override
    public Type getIdentifierType() {
        return IntegerType.INSTANCE;
    }

    @Override
    public boolean hasIndex() {
        return getCollectionType() instanceof ListType
            || getCollectionType() instanceof MapType;
    }

    @Override
    public EntityPersister getElementPersister() {
        if (elementType.isEntityType()) {
            return factory.getMetamodel()
                    .entityPersister(elementType.getName());
        }
        else {
            return null;
        }
    }

    @Override
    public SessionFactoryImplementor getFactory() {
        return factory;
    }

    @Override
    public boolean isOneToMany() {
        return elementType.isEntityType();
    }

    @Override
    public Serializable[] getCollectionSpaces() {
        return new Serializable[] {role};
    }

    @Override
    public String getMappedByProperty() {
        return null;
    }

    @Override
    public CollectionPersister getCollectionPersister() {
        return this;
    }

    @Override
    public String[] getIndexColumnNames() {
        return INDEX_COLUMN;
    }

    @Override
    public String[] getIndexColumnNames(String alias) {
        return INDEX_COLUMN;
    }

    @Override
    public String[] getIndexFormulas() {
        return null;
    }

    @Override
    public String[] getElementColumnNames(String alias) {
        return new String[] {""};
    }

    @Override
    public String[] getElementColumnNames() {
        return new String[] {""};
    }

    @Override
    public FetchMode getFetchMode() {
        return FetchMode.DEFAULT;
    }

    @Override
    public String getTableName() {
        return role;
    }

    @Override
    public String[] getKeyColumnNames() {
        return ID_COLUMN;
    }

    @Override
    public boolean isCollection() {
        return true;
    }

    @Override
    public boolean consumesCollectionAlias() {
        return true;
    }

    @Override
    public String[] toColumns(String alias, String propertyName) {
        return new String[] {""};
    }

    @Override
    public String[] toColumns(String propertyName) {
        return new String[] {""};
    }

}
