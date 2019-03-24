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
import org.hibernate.persister.collection.CollectionPropertyMapping;
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

import static org.hibernate.query.validator.MockSessionFactory.typeHelper;

class MockCollectionPersister implements QueryableCollection {

    private String role;
    private String elementClassName;
    private SessionFactoryImplementor factory;
    private String elementEntityName;
    private CollectionType collectionType;
    private String ownerEntityName;

    private CollectionPropertyMapping collectionPropertyMapping = new CollectionPropertyMapping(this);

    static MockCollectionPersister createAssociationCollection(String role,
                                                        CollectionType collectionType,
                                                        String ownerEntityName, String elementEntityName,
                                                        SessionFactoryImplementor factory) {
        return new MockCollectionPersister(role, collectionType, ownerEntityName, elementEntityName, null, factory);
    }

    static MockCollectionPersister createElementCollection(String role,
                                                    CollectionType collectionType,
                                                    String ownerEntityName, String elementClassName,
                                                    SessionFactoryImplementor factory) {
        return new MockCollectionPersister(role, collectionType, ownerEntityName, null, elementClassName, factory);
    }

    private MockCollectionPersister(String role, CollectionType collectionType,
                            String ownerEntityName, String elementEntityName,
                            String elementClassName, SessionFactoryImplementor factory) {
        this.role = role;
        this.collectionType = collectionType;
        this.ownerEntityName = ownerEntityName;
        this.elementEntityName = elementEntityName;
        this.elementClassName = elementClassName;
        this.factory = factory;
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

    @Override
    public Type toType(String propertyName) throws QueryException {
        return elementEntityName == null ? null :
                //this is needed, but why?
                getElementPersister().getPropertyType(propertyName);
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
        return elementEntityName==null ?
                typeHelper.basic(elementClassName) :
                typeHelper.entity(elementEntityName);
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
        return elementEntityName==null ? null :
                factory.getMetamodel().entityPersister(elementEntityName);
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
        return elementEntityName!=null;
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
        return new Serializable[0];
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
        return new String[] {""};
    }

    @Override
    public String[] getIndexFormulas() {
        return new String[0];
    }

    @Override
    public String[] getIndexColumnNames(String alias) {
        return new String[] {""};
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
        return new String[] {""};
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
