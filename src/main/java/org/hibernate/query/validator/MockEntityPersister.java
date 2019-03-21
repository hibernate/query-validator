package org.hibernate.query.validator;

import org.hibernate.*;
import org.hibernate.boot.spi.MetadataImplementor;
import org.hibernate.bytecode.spi.BytecodeEnhancementMetadata;
import org.hibernate.cache.spi.access.EntityDataAccess;
import org.hibernate.cache.spi.access.NaturalIdDataAccess;
import org.hibernate.cache.spi.entry.CacheEntry;
import org.hibernate.cache.spi.entry.CacheEntryStructure;
import org.hibernate.engine.spi.*;
import org.hibernate.id.IdentifierGenerator;
import org.hibernate.internal.FilterAliasGenerator;
import org.hibernate.mapping.KeyValue;
import org.hibernate.mapping.RootClass;
import org.hibernate.mapping.SimpleValue;
import org.hibernate.metadata.ClassMetadata;
import org.hibernate.metamodel.model.domain.NavigableRole;
import org.hibernate.persister.entity.*;
import org.hibernate.persister.walking.spi.AttributeDefinition;
import org.hibernate.persister.walking.spi.EntityIdentifierDefinition;
import org.hibernate.sql.SelectFragment;
import org.hibernate.tuple.entity.EntityMetamodel;
import org.hibernate.tuple.entity.EntityTuplizer;
import org.hibernate.type.ManyToOneType;
import org.hibernate.type.Type;
import org.hibernate.type.TypeFactory;
import org.hibernate.type.VersionType;
import org.hibernate.type.spi.TypeConfiguration;

import java.io.Serializable;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;

class MockEntityPersister implements EntityPersister, Queryable {

    private final String entityName;
    private SessionFactoryImplementor factory;

    MockEntityPersister(String entityName, SessionFactoryImplementor factory) {
        this.entityName = entityName;
        this.factory = factory;
    }

    @Override
    public SessionFactoryImplementor getFactory() {
        return factory;
    }

    @Override
    public String getEntityName() {
        return entityName;
    }

    @Override
    public EntityMetamodel getEntityMetamodel() {
        return new EntityMetamodel(new RootClass(null) {
            @Override
            public KeyValue getIdentifier() {
                return new SimpleValue((MetadataImplementor) null) {
                    @Override
                    public Type getType() throws MappingException {
                        return getIdentifierType();
                    }
                    @Override
                    public String getTypeName() {
                        return getType().getName();
                    }
                };
            }
        }, this, factory);
    }

    @Override
    public Type toType(String s) throws QueryException {
        return null;
    }

    @Override
    public String[] toColumns(String s, String s1) throws QueryException {
        return new String[] { s + s1 };
    }

    @Override
    public String[] toColumns(String s) throws QueryException, UnsupportedOperationException {
        return new String[] { s };
    }

    @Override
    public Type getType() {
        return new ManyToOneType(new TypeFactory.TypeScope() {
            @Override
            public TypeConfiguration getTypeConfiguration() {
                return null;
            }
        }, entityName);
    }

    @Override
    public void generateEntityDefinition() {}

    @Override
    public void postInstantiate() throws MappingException {}

    @Override
    public NavigableRole getNavigableRole() {
        return null;
    }

    @Override
    public EntityEntryFactory getEntityEntryFactory() {
        return null;
    }

    @Override
    public String getRootEntityName() {
        return null;
    }

    @Override
    public boolean isSubclassEntityName(String s) {
        return false;
    }

    @Override
    public Serializable[] getPropertySpaces() {
        return new Serializable[0];
    }

    @Override
    public Serializable[] getQuerySpaces() {
        return new Serializable[0];
    }

    @Override
    public boolean hasProxy() {
        return false;
    }

    @Override
    public boolean hasCollections() {
        return false;
    }

    @Override
    public boolean hasMutableProperties() {
        return false;
    }

    @Override
    public boolean hasSubselectLoadableCollections() {
        return false;
    }

    @Override
    public boolean hasCascades() {
        return false;
    }

    @Override
    public boolean isMutable() {
        return false;
    }

    @Override
    public boolean isInherited() {
        return false;
    }

    @Override
    public boolean isIdentifierAssignedByInsert() {
        return false;
    }

    @Override
    public Type getPropertyType(String s) throws MappingException {
        return null;
    }

    @Override
    public int[] findDirty(Object[] objects, Object[] objects1, Object o, SharedSessionContractImplementor sharedSessionContractImplementor) {
        return new int[0];
    }

    @Override
    public int[] findModified(Object[] objects, Object[] objects1, Object o, SharedSessionContractImplementor sharedSessionContractImplementor) {
        return new int[0];
    }

    @Override
    public boolean hasIdentifierProperty() {
        return false;
    }

    @Override
    public boolean canExtractIdOutOfEntity() {
        return false;
    }

    @Override
    public boolean isVersioned() {
        return false;
    }

    @Override
    public VersionType getVersionType() {
        return null;
    }

    @Override
    public int getVersionProperty() {
        return 0;
    }

    @Override
    public boolean hasNaturalIdentifier() {
        return false;
    }

    @Override
    public int[] getNaturalIdentifierProperties() {
        return new int[0];
    }

    @Override
    public Object[] getNaturalIdentifierSnapshot(Serializable serializable, SharedSessionContractImplementor sharedSessionContractImplementor) {
        return new Object[0];
    }

    @Override
    public IdentifierGenerator getIdentifierGenerator() {
        return null;
    }

    @Override
    public boolean hasLazyProperties() {
        return false;
    }

    @Override
    public Serializable loadEntityIdByNaturalId(Object[] objects, LockOptions lockOptions, SharedSessionContractImplementor sharedSessionContractImplementor) {
        return null;
    }

    @Override
    public Object load(Serializable serializable, Object o, LockMode lockMode, SharedSessionContractImplementor sharedSessionContractImplementor) throws HibernateException {
        return null;
    }

    @Override
    public Object load(Serializable serializable, Object o, LockOptions lockOptions, SharedSessionContractImplementor sharedSessionContractImplementor) throws HibernateException {
        return null;
    }

    @Override
    public List multiLoad(Serializable[] serializables, SharedSessionContractImplementor sharedSessionContractImplementor, MultiLoadOptions multiLoadOptions) {
        return null;
    }

    @Override
    public void lock(Serializable serializable, Object o, Object o1, LockMode lockMode, SharedSessionContractImplementor sharedSessionContractImplementor) throws HibernateException {}

    @Override
    public void lock(Serializable serializable, Object o, Object o1, LockOptions lockOptions, SharedSessionContractImplementor sharedSessionContractImplementor) throws HibernateException {}

    @Override
    public void insert(Serializable serializable, Object[] objects, Object o, SharedSessionContractImplementor sharedSessionContractImplementor) throws HibernateException {}

    @Override
    public Serializable insert(Object[] objects, Object o, SharedSessionContractImplementor sharedSessionContractImplementor) throws HibernateException {
        return null;
    }

    @Override
    public void delete(Serializable serializable, Object o, Object o1, SharedSessionContractImplementor sharedSessionContractImplementor) throws HibernateException {}

    @Override
    public void update(Serializable serializable, Object[] objects, int[] ints, boolean b, Object[] objects1, Object o, Object o1, Object o2, SharedSessionContractImplementor sharedSessionContractImplementor) throws HibernateException {}

    @Override
    public Type[] getPropertyTypes() {
        return new Type[0];
    }

    @Override
    public String[] getPropertyNames() {
        return new String[0];
    }

    @Override
    public boolean[] getPropertyInsertability() {
        return new boolean[0];
    }

    @Override
    public ValueInclusion[] getPropertyInsertGenerationInclusions() {
        return new ValueInclusion[0];
    }

    @Override
    public ValueInclusion[] getPropertyUpdateGenerationInclusions() {
        return new ValueInclusion[0];
    }

    @Override
    public boolean[] getPropertyUpdateability() {
        return new boolean[0];
    }

    @Override
    public boolean[] getPropertyCheckability() {
        return new boolean[0];
    }

    @Override
    public boolean[] getPropertyNullability() {
        return new boolean[0];
    }

    @Override
    public boolean[] getPropertyVersionability() {
        return new boolean[0];
    }

    @Override
    public boolean[] getPropertyLaziness() {
        return new boolean[0];
    }

    @Override
    public CascadeStyle[] getPropertyCascadeStyles() {
        return new CascadeStyle[0];
    }

    @Override
    public Type getIdentifierType() {
        return null;
    }

    @Override
    public String getIdentifierPropertyName() {
        return null;
    }

    @Override
    public boolean isCacheInvalidationRequired() {
        return false;
    }

    @Override
    public boolean isLazyPropertiesCacheable() {
        return false;
    }

    @Override
    public boolean canReadFromCache() {
        return false;
    }

    @Override
    public boolean canWriteToCache() {
        return false;
    }

    @Override
    public boolean hasCache() {
        return false;
    }

    @Override
    public EntityDataAccess getCacheAccessStrategy() {
        return null;
    }

    @Override
    public CacheEntryStructure getCacheEntryStructure() {
        return null;
    }

    @Override
    public CacheEntry buildCacheEntry(Object o, Object[] objects, Object o1, SharedSessionContractImplementor sharedSessionContractImplementor) {
        return null;
    }

    @Override
    public boolean hasNaturalIdCache() {
        return false;
    }

    @Override
    public NaturalIdDataAccess getNaturalIdCacheAccessStrategy() {
        return null;
    }

    @Override
    public ClassMetadata getClassMetadata() {
        return null;
    }

    @Override
    public boolean isBatchLoadable() {
        return false;
    }

    @Override
    public boolean isSelectBeforeUpdateRequired() {
        return false;
    }

    @Override
    public Object[] getDatabaseSnapshot(Serializable serializable, SharedSessionContractImplementor sharedSessionContractImplementor) throws HibernateException {
        return new Object[0];
    }

    @Override
    public Serializable getIdByUniqueKey(Serializable serializable, String s, SharedSessionContractImplementor sharedSessionContractImplementor) {
        return null;
    }

    @Override
    public Object getCurrentVersion(Serializable serializable, SharedSessionContractImplementor sharedSessionContractImplementor) throws HibernateException {
        return null;
    }

    @Override
    public Object forceVersionIncrement(Serializable serializable, Object o, SharedSessionContractImplementor sharedSessionContractImplementor) throws HibernateException {
        return null;
    }

    @Override
    public boolean isInstrumented() {
        return false;
    }

    @Override
    public boolean hasInsertGeneratedProperties() {
        return false;
    }

    @Override
    public boolean hasUpdateGeneratedProperties() {
        return false;
    }

    @Override
    public boolean isVersionPropertyGenerated() {
        return false;
    }

    @Override
    public void afterInitialize(Object o, SharedSessionContractImplementor sharedSessionContractImplementor) {}

    @Override
    public void afterReassociate(Object o, SharedSessionContractImplementor sharedSessionContractImplementor) {}

    @Override
    public Object createProxy(Serializable serializable, SharedSessionContractImplementor sharedSessionContractImplementor) throws HibernateException {
        return null;
    }

    @Override
    public Boolean isTransient(Object o, SharedSessionContractImplementor sharedSessionContractImplementor) throws HibernateException {
        return null;
    }

    @Override
    public Object[] getPropertyValuesToInsert(Object o, Map map, SharedSessionContractImplementor sharedSessionContractImplementor) throws HibernateException {
        return new Object[0];
    }

    @Override
    public void processInsertGeneratedProperties(Serializable serializable, Object o, Object[] objects, SharedSessionContractImplementor sharedSessionContractImplementor) {}

    @Override
    public void processUpdateGeneratedProperties(Serializable serializable, Object o, Object[] objects, SharedSessionContractImplementor sharedSessionContractImplementor) {}

    @Override
    public Class getMappedClass() {
        return null;
    }

    @Override
    public boolean implementsLifecycle() {
        return false;
    }

    @Override
    public Class getConcreteProxyClass() {
        return null;
    }

    @Override
    public void setPropertyValues(Object o, Object[] objects) {}

    @Override
    public void setPropertyValue(Object o, int i, Object o1) {}

    @Override
    public Object[] getPropertyValues(Object o) {
        return new Object[0];
    }

    @Override
    public Object getPropertyValue(Object o, int i) throws HibernateException {
        return null;
    }

    @Override
    public Object getPropertyValue(Object o, String s) {
        return null;
    }

    @Override
    public Serializable getIdentifier(Object o) throws HibernateException {
        return null;
    }

    @Override
    public Serializable getIdentifier(Object o, SharedSessionContractImplementor sharedSessionContractImplementor) {
        return null;
    }

    @Override
    public void setIdentifier(Object o, Serializable serializable, SharedSessionContractImplementor sharedSessionContractImplementor) {}

    @Override
    public Object getVersion(Object o) throws HibernateException {
        return null;
    }

    @Override
    public Object instantiate(Serializable serializable, SharedSessionContractImplementor sharedSessionContractImplementor) {
        return null;
    }

    @Override
    public boolean isInstance(Object o) {
        return false;
    }

    @Override
    public boolean hasUninitializedLazyProperties(Object o) {
        return false;
    }

    @Override
    public void resetIdentifier(Object o, Serializable serializable, Object o1, SharedSessionContractImplementor sharedSessionContractImplementor) {}

    @Override
    public EntityPersister getSubclassEntityPersister(Object o, SessionFactoryImplementor sessionFactoryImplementor) {
        return null;
    }

    @Override
    public EntityMode getEntityMode() {
        return EntityMode.POJO;
    }

    @Override
    public EntityTuplizer getEntityTuplizer() {
        return null;
    }

    @Override
    public BytecodeEnhancementMetadata getInstrumentationMetadata() {
        return null;
    }

    @Override
    public FilterAliasGenerator getFilterAliasGenerator(String s) {
        return null;
    }

    @Override
    public int[] resolveAttributeIndexes(String[] strings) {
        return new int[0];
    }

    @Override
    public boolean canUseReferenceCacheEntries() {
        return false;
    }

    @Override
    public EntityPersister getEntityPersister() {
        return this;
    }

    @Override
    public EntityIdentifierDefinition getEntityKeyDefinition() {
        return null;
    }

    @Override
    public Iterable<AttributeDefinition> getAttributes() {
        return null;
    }

    @Override
    public boolean isAbstract() {
        return false;
    }

    @Override
    public void registerAffectingFetchProfile(String s) {}

    @Override
    public String getTableAliasForColumn(String s, String s1) {
        return "";
    }

    @Override
    public boolean isExplicitPolymorphism() {
        return false;
    }

    @Override
    public String getMappedSuperclass() {
        return null;
    }

    @Override
    public String getDiscriminatorSQLValue() {
        return null;
    }

    @Override
    public String identifierSelectFragment(String s, String s1) {
        return "";
    }

    @Override
    public String propertySelectFragment(String s, String s1, boolean b) {
        return "";
    }

    @Override
    public SelectFragment propertySelectFragmentFragment(String s, String s1, boolean b) {
        return null;
    }

    @Override
    public boolean hasSubclasses() {
        return false;
    }

    @Override
    public Type getDiscriminatorType() {
        return null;
    }

    @Override
    public Object getDiscriminatorValue() {
        return null;
    }

    @Override
    public String getSubclassForDiscriminatorValue(Object o) {
        return null;
    }

    @Override
    public String[] getIdentifierColumnNames() {
        return new String[0];
    }

    @Override
    public String[] getIdentifierAliases(String s) {
        return new String[0];
    }

    @Override
    public String[] getPropertyAliases(String s, int i) {
        return new String[0];
    }

    @Override
    public String[] getPropertyColumnNames(int i) {
        return new String[0];
    }

    @Override
    public String getDiscriminatorAlias(String s) {
        return null;
    }

    @Override
    public String getDiscriminatorColumnName() {
        return null;
    }

    @Override
    public boolean hasRowId() {
        return false;
    }

    @Override
    public Object[] hydrate(ResultSet resultSet, Serializable serializable, Object o, Loadable loadable, String[][] strings, boolean b, SharedSessionContractImplementor sharedSessionContractImplementor) throws SQLException, HibernateException {
        return new Object[0];
    }

    @Override
    public boolean isMultiTable() {
        return false;
    }

    @Override
    public String[] getConstraintOrderedTableNameClosure() {
        return new String[0];
    }

    @Override
    public String[][] getContraintOrderedTableKeyColumnClosure() {
        return new String[0][];
    }

    @Override
    public int getSubclassPropertyTableNumber(String s) {
        return 0;
    }

    @Override
    public Declarer getSubclassPropertyDeclarer(String s) {
        return null;
    }

    @Override
    public String getSubclassTableName(int i) {
        return "";
    }

    @Override
    public boolean isVersionPropertyInsertable() {
        return false;
    }

    @Override
    public String generateFilterConditionAlias(String s) {
        return null;
    }

    @Override
    public DiscriminatorMetadata getTypeDiscriminatorMetadata() {
        return null;
    }

    @Override
    public String[][] getSubclassPropertyFormulaTemplateClosure() {
        return new String[0][];
    }

    @Override
    public String getName() {
        return getEntityName();
    }

    @Override
    public String getTableName() {
        return "";
    }

    @Override
    public String selectFragment(Joinable joinable, String s, String s1, String s2, String s3, boolean b) {
        return "";
    }

    @Override
    public String whereJoinFragment(String s, boolean b, boolean b1) {
        return "";
    }

    @Override
    public String whereJoinFragment(String s, boolean b, boolean b1, Set<String> set) {
        return "";
    }

    @Override
    public String fromJoinFragment(String s, boolean b, boolean b1) {
        return "";
    }

    @Override
    public String fromJoinFragment(String s, boolean b, boolean b1, Set<String> set) {
        return "";
    }

    @Override
    public String[] getKeyColumnNames() {
        return new String[] {"id"};
    }

    @Override
    public String filterFragment(String s, Map map) throws MappingException {
        return "";
    }

    @Override
    public String filterFragment(String s, Map map, Set<String> set) throws MappingException {
        return "";
    }

    @Override
    public String oneToManyFilterFragment(String s) throws MappingException {
        return "";
    }

    @Override
    public String oneToManyFilterFragment(String s, Set<String> set) {
        return "";
    }

    @Override
    public boolean isCollection() {
        return false;
    }

    @Override
    public boolean consumesEntityAlias() {
        return true;
    }

    @Override
    public boolean consumesCollectionAlias() {
        return false;
    }

    @Override
    public String toString() {
        return "EntityPersister[" + entityName + "]";
    }
}
