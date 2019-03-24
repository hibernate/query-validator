package org.hibernate.query.validator;

import org.hibernate.FetchMode;
import org.hibernate.HibernateException;
import org.hibernate.MappingException;
import org.hibernate.QueryException;
import org.hibernate.cache.spi.access.CollectionDataAccess;
import org.hibernate.cache.spi.entry.CacheEntryStructure;
import org.hibernate.collection.spi.PersistentCollection;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.IdentifierGenerator;
import org.hibernate.metadata.CollectionMetadata;
import org.hibernate.metamodel.model.domain.NavigableRole;
import org.hibernate.persister.collection.CollectionPersister;
import org.hibernate.persister.collection.QueryableCollection;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.persister.entity.Joinable;
import org.hibernate.persister.walking.spi.CollectionElementDefinition;
import org.hibernate.persister.walking.spi.CollectionIndexDefinition;
import org.hibernate.type.*;

import java.io.Serializable;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;

import static org.hibernate.internal.util.StringHelper.root;

abstract class MockCollectionPersister implements QueryableCollection {

    private static final Serializable[] NO_SPACES = new Serializable[0];
    private static final String[] ID_COLUMN = {"id"};
    private static final String[] INDEX_COLUMN = {"pos"};

    private String role;
    private SessionFactoryImplementor factory;
    private CollectionType collectionType;
    private String ownerEntityName;
    private Type elementType;

    MockCollectionPersister(String role, CollectionType collectionType,
                            Type elementType,
                            SessionFactoryImplementor factory) {
        this.role = role;
        this.collectionType = collectionType;
        this.elementType = elementType;
        this.factory = factory;
        this.ownerEntityName = root(role);
    }

    public String getOwnerEntityName() {
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
        } else {
            return null;
        }
    }

    @Override
    public SessionFactoryImplementor getFactory() {
        return factory;
    }

    @Override
    public Class getElementClass() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object readKey(ResultSet rs, String[] keyAliases, SharedSessionContractImplementor session) throws HibernateException, SQLException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object readElement(ResultSet rs, Object owner, String[] columnAliases, SharedSessionContractImplementor session) throws HibernateException, SQLException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object readIndex(ResultSet rs, String[] columnAliases, SharedSessionContractImplementor session) throws HibernateException, SQLException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object readIdentifier(ResultSet rs, String columnAlias, SharedSessionContractImplementor session) throws HibernateException, SQLException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isPrimitiveArray() {
        return false;
    }

    @Override
    public boolean isArray() {
        return false;
    }

    @Override
    public boolean isOneToMany() {
        return elementType.isEntityType();
    }

    @Override
    public boolean isManyToMany() {
        return false;
    }

    @Override
    public void initialize(Serializable key, SharedSessionContractImplementor session)
            throws HibernateException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean hasCache() {
        return false;
    }

    @Override
    public CollectionDataAccess getCacheAccessStrategy() {
        throw new UnsupportedOperationException();
    }

    @Override
    public NavigableRole getNavigableRole() {
        throw new UnsupportedOperationException();
    }

    @Override
    public CacheEntryStructure getCacheEntryStructure() {
        throw new UnsupportedOperationException();
    }
    @Override
    public boolean isLazy() {
        return false;
    }

    @Override
    public boolean isInverse() {
        return false;
    }

    @Override
    public void remove(Serializable id, SharedSessionContractImplementor session) throws HibernateException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void recreate(PersistentCollection collection, Serializable key, SharedSessionContractImplementor session) throws HibernateException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteRows(PersistentCollection collection, Serializable key, SharedSessionContractImplementor session) throws HibernateException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateRows(PersistentCollection collection, Serializable key, SharedSessionContractImplementor session) throws HibernateException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void insertRows(PersistentCollection collection, Serializable key, SharedSessionContractImplementor session) throws HibernateException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void processQueuedOps(PersistentCollection collection, Serializable key, SharedSessionContractImplementor session) throws HibernateException {
        throw new UnsupportedOperationException();
    }

    @Override
    public IdentifierGenerator getIdentifierGenerator() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean hasOrphanDelete() {
        return false;
    }

    @Override
    public boolean hasOrdering() {
        return false;
    }

    @Override
    public boolean hasManyToManyOrdering() {
        return false;
    }

    @Override
    public Serializable[] getCollectionSpaces() {
        return NO_SPACES;
    }

    @Override
    public CollectionMetadata getCollectionMetadata() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isCascadeDeleteEnabled() {
        return false;
    }

    @Override
    public boolean isVersioned() {
        return false;
    }

    @Override
    public boolean isMutable() {
        return false;
    }

    @Override
    public void postInstantiate() throws MappingException {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getManyToManyFilterFragment(String alias, Map enabledFilters) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isAffectedByEnabledFilters(SharedSessionContractImplementor session) {
        return false;
    }

    @Override
    public String[] getKeyColumnAliases(String suffix) {
        return new String[0];
    }

    @Override
    public String[] getIndexColumnAliases(String suffix) {
        return new String[0];
    }

    @Override
    public String[] getElementColumnAliases(String suffix) {
        return new String[0];
    }

    @Override
    public String getIdentifierColumnAlias(String suffix) {
        return "";
    }

    @Override
    public boolean isExtraLazy() {
        return false;
    }

    @Override
    public int getSize(Serializable key, SharedSessionContractImplementor session) {
        return 0;
    }

    @Override
    public boolean indexExists(Serializable key, Object index, SharedSessionContractImplementor session) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean elementExists(Serializable key, Object element, SharedSessionContractImplementor session) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object getElementByIndex(Serializable key, Object index, SharedSessionContractImplementor session, Object owner) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getBatchSize() {
        return 0;
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
    public CollectionIndexDefinition getIndexDefinition() {
        throw new UnsupportedOperationException();
    }

    @Override
    public CollectionElementDefinition getElementDefinition() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String selectFragment(String alias, String columnSuffix) {
        return "";
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
    public String getSQLOrderByString(String alias) {
        return "";
    }

    @Override
    public String getManyToManyOrderByString(String alias) {
        return "";
    }

    @Override
    public boolean hasWhere() {
        return false;
    }

    @Override
    public FetchMode getFetchMode() {
        return FetchMode.DEFAULT;
    }

    @Override
    public String getTableName() {
        return "";
    }

    @Override
    public String selectFragment(Joinable rhs, String rhsAlias, String lhsAlias, String currentEntitySuffix, String currentCollectionSuffix, boolean includeCollectionColumns) {
        return "";
    }

    @Override
    public String whereJoinFragment(String alias, boolean innerJoin, boolean includeSubclasses) {
        return "";
    }

    @Override
    public String whereJoinFragment(String alias, boolean innerJoin, boolean includeSubclasses, Set<String> treatAsDeclarations) {
        return "";
    }

    @Override
    public String fromJoinFragment(String alias, boolean innerJoin, boolean includeSubclasses) {
        return "";
    }

    @Override
    public String fromJoinFragment(String alias, boolean innerJoin, boolean includeSubclasses, Set<String> treatAsDeclarations) {
        return "";
    }

    @Override
    public String[] getKeyColumnNames() {
        return ID_COLUMN;
    }

    @Override
    public String filterFragment(String alias, Map enabledFilters) throws MappingException {
        return "";
    }

    @Override
    public String filterFragment(String alias, Map enabledFilters, Set<String> treatAsDeclarations) throws MappingException {
        return "";
    }

    @Override
    public String oneToManyFilterFragment(String alias) throws MappingException {
        return "";
    }

    @Override
    public String oneToManyFilterFragment(String alias, Set<String> treatAsDeclarations) {
        return "";
    }

    @Override
    public boolean isCollection() {
        return true;
    }

    @Override
    public boolean consumesEntityAlias() {
        return false;
    }

    @Override
    public boolean consumesCollectionAlias() {
        return true;
    }

    @Override
    public String[] toColumns(String alias, String propertyName) throws QueryException {
        return new String[] {""};
    }

    @Override
    public String[] toColumns(String propertyName) throws QueryException, UnsupportedOperationException {
        return new String[] {""};
    }

}
