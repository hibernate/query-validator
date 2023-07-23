package org.hibernate.query.validator;

import org.hibernate.CustomEntityDirtinessStrategy;
import org.hibernate.EntityNameResolver;
import org.hibernate.MappingException;
import org.hibernate.SessionFactoryObserver;
import org.hibernate.TimeZoneStorageStrategy;
import org.hibernate.boot.internal.DefaultCustomEntityDirtinessStrategy;
import org.hibernate.boot.internal.MetadataImpl;
import org.hibernate.boot.internal.StandardEntityNotFoundDelegate;
import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.naming.ImplicitNamingStrategy;
import org.hibernate.boot.model.naming.ImplicitNamingStrategyJpaCompliantImpl;
import org.hibernate.boot.model.naming.PhysicalNamingStrategy;
import org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl;
import org.hibernate.boot.model.relational.Database;
import org.hibernate.boot.model.relational.SqlStringGenerationContext;
import org.hibernate.boot.registry.BootstrapServiceRegistryBuilder;
import org.hibernate.boot.registry.classloading.internal.ClassLoaderServiceImpl;
import org.hibernate.boot.registry.classloading.spi.ClassLoadingException;
import org.hibernate.boot.registry.internal.StandardServiceRegistryImpl;
import org.hibernate.boot.spi.BootstrapContext;
import org.hibernate.boot.spi.MappingDefaults;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.boot.spi.MetadataBuildingOptions;
import org.hibernate.boot.spi.MetadataImplementor;
import org.hibernate.boot.spi.SessionFactoryOptions;
import org.hibernate.cache.internal.DisabledCaching;
import org.hibernate.cache.spi.CacheImplementor;
import org.hibernate.cache.spi.access.AccessType;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.hibernate.dialect.Dialect;
import org.hibernate.engine.jdbc.spi.JdbcServices;
import org.hibernate.engine.query.internal.NativeQueryInterpreterStandardImpl;
import org.hibernate.engine.query.spi.NativeQueryInterpreter;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.graph.spi.RootGraphImplementor;
import org.hibernate.id.factory.IdentifierGeneratorFactory;
import org.hibernate.id.factory.internal.StandardIdentifierGeneratorFactory;
import org.hibernate.internal.FastSessionServices;
import org.hibernate.jpa.internal.MutableJpaComplianceImpl;
import org.hibernate.jpa.spi.JpaCompliance;
import org.hibernate.jpa.spi.MutableJpaCompliance;
import org.hibernate.loader.BatchFetchStyle;
import org.hibernate.mapping.Property;
import org.hibernate.metamodel.AttributeClassification;
import org.hibernate.metamodel.CollectionClassification;
import org.hibernate.metamodel.internal.JpaMetaModelPopulationSetting;
import org.hibernate.metamodel.internal.JpaStaticMetaModelPopulationSetting;
import org.hibernate.metamodel.internal.MetadataContext;
import org.hibernate.metamodel.internal.RuntimeMetamodelsImpl;
import org.hibernate.metamodel.mapping.JdbcMapping;
import org.hibernate.metamodel.model.domain.BasicDomainType;
import org.hibernate.metamodel.model.domain.DomainType;
import org.hibernate.metamodel.model.domain.EntityDomainType;
import org.hibernate.metamodel.model.domain.ManagedDomainType;
import org.hibernate.metamodel.model.domain.PersistentAttribute;
import org.hibernate.metamodel.model.domain.internal.AbstractAttribute;
import org.hibernate.metamodel.model.domain.internal.AbstractPluralAttribute;
import org.hibernate.metamodel.model.domain.internal.BagAttributeImpl;
import org.hibernate.metamodel.model.domain.internal.BasicTypeImpl;
import org.hibernate.metamodel.model.domain.internal.EmbeddableTypeImpl;
import org.hibernate.metamodel.model.domain.internal.EntityTypeImpl;
import org.hibernate.metamodel.model.domain.internal.JpaMetamodelImpl;
import org.hibernate.metamodel.model.domain.internal.ListAttributeImpl;
import org.hibernate.metamodel.model.domain.internal.MapAttributeImpl;
import org.hibernate.metamodel.model.domain.internal.MappingMetamodelImpl;
import org.hibernate.metamodel.model.domain.internal.PluralAttributeBuilder;
import org.hibernate.metamodel.model.domain.internal.SetAttributeImpl;
import org.hibernate.metamodel.model.domain.internal.SingularAttributeImpl;
import org.hibernate.metamodel.model.domain.spi.JpaMetamodelImplementor;
import org.hibernate.metamodel.spi.MappingMetamodelImplementor;
import org.hibernate.metamodel.spi.MetamodelImplementor;
import org.hibernate.metamodel.spi.RuntimeMetamodelsImplementor;
import org.hibernate.metamodel.spi.RuntimeModelCreationContext;
import org.hibernate.persister.collection.CollectionPersister;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.proxy.EntityNotFoundDelegate;
import org.hibernate.query.criteria.ValueHandlingMode;
import org.hibernate.query.hql.HqlTranslator;
import org.hibernate.query.hql.internal.StandardHqlTranslator;
import org.hibernate.query.internal.NamedObjectRepositoryImpl;
import org.hibernate.query.internal.QueryInterpretationCacheDisabledImpl;
import org.hibernate.query.named.NamedObjectRepository;
import org.hibernate.query.spi.QueryEngine;
import org.hibernate.query.spi.QueryInterpretationCache;
import org.hibernate.query.sqm.NodeBuilder;
import org.hibernate.query.sqm.function.SqmFunctionRegistry;
import org.hibernate.query.sqm.internal.SqmCriteriaNodeBuilder;
import org.hibernate.query.sqm.sql.SqmTranslatorFactory;
import org.hibernate.query.sqm.sql.StandardSqmTranslatorFactory;
import org.hibernate.stat.internal.StatisticsImpl;
import org.hibernate.stat.spi.StatisticsImplementor;
import org.hibernate.type.BagType;
import org.hibernate.type.BasicType;
import org.hibernate.type.CollectionType;
import org.hibernate.type.CompositeType;
import org.hibernate.type.ListType;
import org.hibernate.type.MapType;
import org.hibernate.type.SetType;
import org.hibernate.type.SqlTypes;
import org.hibernate.type.Type;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.java.spi.UnknownBasicJavaType;
import org.hibernate.type.descriptor.jdbc.JdbcTypeIndicators;
import org.hibernate.type.spi.TypeConfiguration;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.Collections.*;

/**
 * @author Gavin King
 */
public abstract class MockSessionFactory
        implements SessionFactoryImplementor, QueryEngine, RuntimeModelCreationContext, MetadataBuildingOptions,
                BootstrapContext, MetadataBuildingContext, FunctionContributions, SessionFactoryOptions, JdbcTypeIndicators {

    // static so other things can get at it
    // TODO: make a static instance of this whole object instead!
    static TypeConfiguration typeConfiguration;

    final StandardServiceRegistryImpl serviceRegistry =
            new StandardServiceRegistryImpl(
                    new BootstrapServiceRegistryBuilder().applyClassLoaderService(new ClassLoaderServiceImpl() {
                        @Override
                        public Class classForName(String className) {
                            try {
                                return super.classForName(className);
                            }
                            catch (ClassLoadingException e) {
                                if ( MockSessionFactory.this.isClassDefined(className) ) {
                                    return Object[].class;
                                }
                                else {
                                    throw e;
                                }
                            }
                        }
                    }).build(),
                    singletonList(MockJdbcServicesInitiator.INSTANCE),
                    emptyList(),
                    emptyMap()
            );

    private final Map<String,MockEntityPersister> entityPersistersByName = new HashMap<>();
    private final Map<String,MockCollectionPersister> collectionPersistersByName = new HashMap<>();

    private final SqmFunctionRegistry functionRegistry = new SqmFunctionRegistry();

    private final MappingMetamodelImpl metamodel = new MockMappingMetamodelImpl();

    private final Database database =
            new Database(this, MockJdbcServicesInitiator.jdbcServices.getJdbcEnvironment());

    private final MetadataImplementor bootModel =
            new MetadataImpl(
                    UUID.randomUUID(),
                    this,
                    emptyMap(),
                    emptyList(),
                    emptyMap(),
                    emptyMap(),
                    emptyMap(),
                    emptyMap(),
                    emptyMap(),
                    emptyMap(),
                    emptyMap(),
                    emptyMap(),
                    emptyMap(),
                    emptyMap(),
                    emptyMap(),
                    emptyMap(),
                    emptyMap(),
                    emptyMap(),
                    database,
                    this
            );

    private final MetadataContext metadataContext =
            new MetadataContext(
                    metamodel.getJpaMetamodel(),
                    metamodel,
                    bootModel,
                    JpaStaticMetaModelPopulationSetting.DISABLED,
                    JpaMetaModelPopulationSetting.DISABLED,
                    this
            );

    public MockSessionFactory() {
        typeConfiguration = new TypeConfiguration();
        typeConfiguration.scope((MetadataBuildingContext) this);
        MockJdbcServicesInitiator.genericDialect.initializeFunctionRegistry(this);
        typeConfiguration.scope((SessionFactoryImplementor) this);
    }

    @Override
    public TypeConfiguration getTypeConfiguration() {
        return typeConfiguration;
    }

    @Override
    public void addObserver(SessionFactoryObserver observer) {
    }

    @Override
    public MetadataBuildingOptions getBuildingOptions() {
        return this;
    }

    @Override
    public PhysicalNamingStrategy getPhysicalNamingStrategy() {
        return new PhysicalNamingStrategyStandardImpl();
    }

    @Override
    public ImplicitNamingStrategy getImplicitNamingStrategy() {
        return new ImplicitNamingStrategyJpaCompliantImpl();
    }

    static CollectionType createCollectionType(String role, String name) {
        switch (name) {
            case "Set":
            case "SortedSet":
                //might actually be a bag!
                //TODO: look for @OrderColumn on the property
                return new SetType(role, null);
            case "List":
            case "SortedList":
                return new ListType(role, null);
            case "Map":
            case "SortedMap":
                return new MapType(role, null);
            default:
                return new BagType(role, null);
        }
    }

    /**
     * Lazily create a {@link MockEntityPersister}
     */
    abstract MockEntityPersister createMockEntityPersister(String entityName);

    /**
     * Lazily create a {@link MockCollectionPersister}
     */
    abstract MockCollectionPersister createMockCollectionPersister(String role);

    abstract boolean isEntityDefined(String entityName);

    abstract boolean isAttributeDefined(String entityName, String fieldName);

    abstract boolean isClassDefined(String qualifiedName);

    abstract boolean isFieldDefined(String qualifiedClassName, String fieldName);

    abstract boolean isConstructorDefined(String qualifiedClassName, List<Type> argumentTypes);

    protected abstract boolean isSubtype(String entityName, String subtypeEntityName);

    private EntityPersister createEntityPersister(String entityName) {
        MockEntityPersister result = entityPersistersByName.get(entityName);
        if (result!=null) return result;
        result = createMockEntityPersister(entityName);
        entityPersistersByName.put(entityName, result);
        return result;
    }

    private CollectionPersister createCollectionPersister(String entityName) {
        MockCollectionPersister result = collectionPersistersByName.get(entityName);
        if (result!=null) return result;
        result = createMockCollectionPersister(entityName);
        collectionPersistersByName.put(entityName, result);
        return result;
    }

    List<MockEntityPersister> getMockEntityPersisters() {
        return entityPersistersByName.values()
                .stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public Type getIdentifierType(String className)
            throws MappingException {
        return createEntityPersister(className)
                .getIdentifierType();
    }

    @Override
    public String getIdentifierPropertyName(String className)
            throws MappingException {
        return createEntityPersister(className)
                .getIdentifierPropertyName();
    }

    @Override
    public Type getReferencedPropertyType(String className, String propertyName)
            throws MappingException {
        return createEntityPersister(className)
                .getPropertyType(propertyName);
    }

    @Override
    public MetamodelImplementor getMetamodel() {
        return metamodel;
    }

    @Override
    public StandardServiceRegistryImpl getServiceRegistry() {
        return serviceRegistry;
    }

    @Override
    public JdbcServices getJdbcServices() {
        return MockJdbcServicesInitiator.jdbcServices;
//        return serviceRegistry.getService(JdbcServices.class);
    }

    @Override
    public String getName() {
        return "mock";
    }

    @Override
    public SessionFactoryOptions getSessionFactoryOptions() {
        return this;
    }

    @Override
    public Set<String> getDefinedFilterNames() {
        return emptySet();
    }

    @Override
    public CacheImplementor getCache() {
        return new DisabledCaching(this);
    }

    @Override
    public EntityNotFoundDelegate getEntityNotFoundDelegate() {
        return new StandardEntityNotFoundDelegate();
    }

    @Override
    public CustomEntityDirtinessStrategy getCustomEntityDirtinessStrategy() {
        return new DefaultCustomEntityDirtinessStrategy();
    }

    @Override
    public CurrentTenantIdentifierResolver getCurrentTenantIdentifierResolver() {
        return null;
    }

    @Override
    public FastSessionServices getFastSessionServices() {
        throw new UnsupportedOperationException();
    }


    @Override
    public void close() {}

    @Override
    public RootGraphImplementor<?> findEntityGraphByName(String s) {
        throw new UnsupportedOperationException();
    }

    static Class<?> toPrimitiveClass(Class<?> type) {
        switch (type.getName()) {
            case "java.lang.Boolean":
                return boolean.class;
            case "java.lang.Character":
                return char.class;
            case "java.lang.Integer":
                return int.class;
            case "java.lang.Short":
                return short.class;
            case "java.lang.Byte":
                return byte.class;
            case "java.lang.Long":
                return long.class;
            case "java.lang.Float":
                return float.class;
            case "java.lang.Double":
                return double.class;
            default:
                return Object.class;
        }
    }

    @Override
    public NativeQueryInterpreter getNativeQueryInterpreter() {
        return new NativeQueryInterpreterStandardImpl();
    }

    @Override
    public QueryInterpretationCache getInterpretationCache() {
        return new QueryInterpretationCacheDisabledImpl(this::getStatistics);
    }

    @Override
    public StatisticsImplementor getStatistics() {
        return new StatisticsImpl(this);
    }

    @Override
    public SqmFunctionRegistry getSqmFunctionRegistry() {
        return functionRegistry;
    }

    @Override
    public NodeBuilder getCriteriaBuilder() {
        return new SqmCriteriaNodeBuilder(
                "",
                "",
                this,
                false,
                ValueHandlingMode.INLINE,
                () -> MockSessionFactory.this
        );
    }

    @Override
    public void validateNamedQueries() {
    }

    @Override
    public NamedObjectRepository getNamedObjectRepository() {
        return new NamedObjectRepositoryImpl(new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>());
    }

    @Override
    public HqlTranslator getHqlTranslator() {
        return new StandardHqlTranslator(MockSessionFactory.this, () -> false);
    }

    @Override
    public SqmTranslatorFactory getSqmTranslatorFactory() {
        return new StandardSqmTranslatorFactory();
    }

    @Override
    public QueryEngine getQueryEngine() {
        return this;
    }

    @Override
    public JpaMetamodelImplementor getJpaMetamodel() {
        return metamodel.getJpaMetamodel();
    }

    @Override
    public MappingMetamodelImplementor getMappingMetamodel() {
        return metamodel;
    }

    @Override
    public RuntimeMetamodelsImplementor getRuntimeMetamodels() {
        RuntimeMetamodelsImpl runtimeMetamodels = new RuntimeMetamodelsImpl();
        runtimeMetamodels.setJpaMetamodel( metamodel.getJpaMetamodel() );
        runtimeMetamodels.setMappingMetamodel( metamodel );
        return runtimeMetamodels;
    }

    @Override
    public boolean isClosed() {
        return false;
    }

    private static final SessionFactoryObserver[] NO_OBSERVERS = new SessionFactoryObserver[0];
    private static final EntityNameResolver[] NO_RESOLVERS = new EntityNameResolver[0];

    static MutableJpaCompliance jpaCompliance = new MutableJpaComplianceImpl(emptyMap());

    @Override
    public MutableJpaCompliance getJpaCompliance() {
        return jpaCompliance;
    }

    @Override
    public String getSessionFactoryName() {
        return "mock";
    }

    @Override
    public String getUuid() {
        return "mock";
    }

    @Override
    public SessionFactoryObserver[] getSessionFactoryObservers() {
        return NO_OBSERVERS;
    }

    @Override
    public EntityNameResolver[] getEntityNameResolvers() {
        return NO_RESOLVERS;
    }

    @Override
    public BatchFetchStyle getBatchFetchStyle() {
        return BatchFetchStyle.LEGACY;
    }

    @Override
    public boolean isDelayBatchFetchLoaderCreationsEnabled() {
        return false;
    }

    @Override
    public Integer getMaximumFetchDepth() {
        return null;
    }

    @Override
    public void setCheckNullability(boolean enabled) {}


    private static class MockMappingDefaults implements MappingDefaults {
        @Override
        public String getImplicitSchemaName() {
            return null;
        }

        @Override
        public String getImplicitCatalogName() {
            return null;
        }

        @Override
        public boolean shouldImplicitlyQuoteIdentifiers() {
            return false;
        }

        @Override
        public String getImplicitIdColumnName() {
            return null;
        }

        @Override
        public String getImplicitTenantIdColumnName() {
            return null;
        }

        @Override
        public String getImplicitDiscriminatorColumnName() {
            return null;
        }

        @Override
        public String getImplicitPackageName() {
            return null;
        }

        @Override
        public boolean isAutoImportEnabled() {
            return false;
        }

        @Override
        public String getImplicitCascadeStyleName() {
            return null;
        }

        @Override
        public String getImplicitPropertyAccessorName() {
            return null;
        }

        @Override
        public boolean areEntitiesImplicitlyLazy() {
            return false;
        }

        @Override
        public boolean areCollectionsImplicitlyLazy() {
            return false;
        }

        @Override
        public AccessType getImplicitCacheAccessType() {
            return null;
        }

        @Override
        public CollectionClassification getImplicitListClassification() {
            return null;
        }
    }

    @Override
    public Dialect getDialect() {
        return MockJdbcServicesInitiator.genericDialect;
    }

    @Override
    public int getPreferredSqlTypeCodeForBoolean() {
        return SqlTypes.BOOLEAN;
    }

    @Override
    public int getPreferredSqlTypeCodeForDuration() {
        return SqlTypes.NUMERIC;
    }

    @Override
    public int getPreferredSqlTypeCodeForUuid() {
        return SqlTypes.UUID;
    }

    @Override
    public int getPreferredSqlTypeCodeForInstant() {
        return SqlTypes.TIMESTAMP_WITH_TIMEZONE;
    }

    @Override
    public int getPreferredSqlTypeCodeForArray() {
        return SqlTypes.ARRAY;
    }

    private class MockMappingMetamodelImpl extends MappingMetamodelImpl {
        public MockMappingMetamodelImpl() {
            super(typeConfiguration, serviceRegistry);
        }

        @Override
        public EntityPersister getEntityDescriptor(String entityName) {
            return createEntityPersister(entityName);
        }

        @Override
        public EntityPersister entityPersister(String entityName)
                throws MappingException {
            return createEntityPersister(entityName);
        }

        @Override
        public EntityPersister locateEntityPersister(String entityName)
                throws MappingException {
            return createEntityPersister(entityName);
        }

        @Override
        public CollectionPersister getCollectionDescriptor(String role) {
            return createCollectionPersister(role);
        }

        @Override
        public CollectionPersister findCollectionDescriptor(String role) {
            return createCollectionPersister(role);
        }

        @Override
        public CollectionPersister collectionPersister(String role) {
            return createCollectionPersister(role);
        }

        @Override
        public JpaMetamodelImplementor getJpaMetamodel() {
            return new MockJpaMetamodelImpl();
        }

        @Override
        public EntityPersister findEntityDescriptor(String entityName) {
            return createEntityPersister(entityName);
        }
    }

    @Override
    public SessionFactoryImplementor getSessionFactory() {
        return MockSessionFactory.this;
    }

    @Override
    public BootstrapContext getBootstrapContext() {
        return this;
    }

    @Override
    public MetadataImplementor getBootModel() {
        return bootModel;
    }

    @Override
    public MappingMetamodelImplementor getDomainModel() {
        return metamodel;
    }

    @Override
    public SqmFunctionRegistry getFunctionRegistry() {
        return functionRegistry;
    }

    @Override
    public Map<String, Object> getSettings() {
        return emptyMap();
    }

    @Override
    public SqlStringGenerationContext getSqlStringGenerationContext() {
        throw new UnsupportedOperationException();
    }

    @Override
    public IdentifierGeneratorFactory getIdentifierGeneratorFactory() {
        return new StandardIdentifierGeneratorFactory(serviceRegistry, true);
    }

    @Override
    public MappingDefaults getMappingDefaults() {
        return new MockMappingDefaults();
    }

    @Override
    public TimeZoneStorageStrategy getDefaultTimeZoneStorageStrategy() {
        return TimeZoneStorageStrategy.NATIVE;
    }

    private class MockJpaMetamodelImpl extends JpaMetamodelImpl {
        public MockJpaMetamodelImpl() {
            super(typeConfiguration, metamodel, serviceRegistry);
        }

        @Override
        public <X> EntityDomainType<X> entity(String entityName) {
            if ( isEntityDefined(entityName) ) {
                return new MockEntityDomainType<>(entityName);
            }
            else {
                return null;
            }
        }

        @Override
        public String qualifyImportableName(String queryName) {
            return queryName;
        }

        @Override
        public <X> ManagedDomainType<X> findManagedType(Class<X> cls) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <X> EntityDomainType<X> findEntityType(Class<X> cls) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <X> ManagedDomainType<X> managedType(Class<X> cls) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <X> EntityDomainType<X> entity(Class<X> cls) {
            throw new UnsupportedOperationException();
        }

        @Override
        public JpaCompliance getJpaCompliance() {
            return jpaCompliance;
        }
    }

    BasicDomainType<?> unknownType = new BasicTypeImpl<>(new UnknownBasicJavaType<>(Object.class));

    class MockEntityDomainType<X> extends EntityTypeImpl<X> {

        public MockEntityDomainType(String entityName) {
            super(entityName, entityName, false, true, false, null, null,
                    metamodel.getJpaMetamodel());
        }

        @Override
        public PersistentAttribute<? super X, ?> findAttribute(String name) {
            PersistentAttribute<? super X, ?> attribute = super.findAttribute(name);
            if (attribute != null) {
                return attribute;
            }
            for (Map.Entry<String, MockEntityPersister> entry : entityPersistersByName.entrySet()) {
                if (!entry.getValue().getEntityName().equals(getHibernateEntityName())
                        && isSubtype(entry.getValue().getEntityName(), getHibernateEntityName())) {
                    return new MockEntityDomainType<>(entry.getValue().getEntityName()).findAttribute(name);
                }
            }
            return null;
        }

        @Override
        public PersistentAttribute<X,?> findDeclaredAttribute(String name) {
            String entityName = getHibernateEntityName();
            return isAttributeDefined(entityName, name)
                    ? createAttribute(name, entityName, getReferencedPropertyType(entityName, name))
                    : null;
        }

        private AbstractAttribute createAttribute(String name, String entityName, Type type) {
            if (type==null) {
                throw new UnsupportedOperationException(entityName + "." + name);
            }
            else if ( type.isCollectionType() ) {
                CollectionType collectionType = (CollectionType) type;
                return createPluralAttribute(collectionType, getElementDomainType(entityName, collectionType), name);
            }
            else if ( type.isEntityType() ) {
                return new SingularAttributeImpl<>(
                        this,
                        name,
                        AttributeClassification.MANY_TO_ONE,
                        new MockEntityDomainType<>(type.getName()),
                        null,
                        null,
                        false,
                        false,
                        true,
                        false,
                        metadataContext
                );
            }
            else if ( type.isComponentType() ) {
                CompositeType compositeType = (CompositeType) type;
                return new SingularAttributeImpl<>(
                        this,
                        name,
                        AttributeClassification.EMBEDDED,
                        createEmbeddableDomainType(entityName, compositeType),
                        null,
                        null,
                        false,
                        false,
                        true,
                        false,
                        metadataContext
                );
            }
            else {
                return new SingularAttributeImpl<>(
                        this,
                        name,
                        AttributeClassification.BASIC,
                        unknownType,
                        type instanceof JdbcMapping
                                ? ((JdbcMapping) type).getJavaTypeDescriptor()
                                : null,
                        null,
                        false,
                        false,
                        true,
                        false,
                        metadataContext
                );
            }
        }

        private DomainType<?> getElementDomainType(String entityName, CollectionType collectionType) {
            Type elementType = collectionType.getElementType(MockSessionFactory.this);
            if ( elementType.isEntityType() ) {
                String associatedEntityName = collectionType.getAssociatedEntityName(MockSessionFactory.this);
                return new MockEntityDomainType<>(associatedEntityName);
            }
            else if ( elementType.isComponentType() ) {
                CompositeType compositeType = (CompositeType) elementType;
                return createEmbeddableDomainType(entityName, compositeType);
            }
            else if ( elementType instanceof BasicType ) {
                return (BasicType<?>) elementType;
            }
            else {
                return unknownType;
            }
        }

        private AbstractPluralAttribute createPluralAttribute(
                CollectionType collectionType,
                DomainType<?> elementDomainType,
                String name) {
            Property property = new Property();
            property.setName(name);
            JavaType<Object> collectionJavaType =
                    typeConfiguration.getJavaTypeRegistry()
                            .getDescriptor(collectionType.getReturnedClass());
            CollectionClassification classification = collectionType.getCollectionClassification();
            switch (classification) {
                case LIST:
                    return new ListAttributeImpl(
                            new PluralAttributeBuilder<>(
                                    collectionJavaType,
                                    true,
                                    AttributeClassification.MANY_TO_MANY,
                                    classification,
                                    elementDomainType,
                                    typeConfiguration.getBasicTypeRegistry()
                                            .getRegisteredType(Integer.class),
                                    MockEntityDomainType.this,
                                    property,
                                    null
                            ),
                            metadataContext
                    );
                case BAG:
                case ID_BAG:
                    return new BagAttributeImpl(
                            new PluralAttributeBuilder<>(
                                    collectionJavaType,
                                    true,
                                    AttributeClassification.MANY_TO_MANY,
                                    classification,
                                    elementDomainType,
                                    null,
                                    MockEntityDomainType.this,
                                    property,
                                    null
                            ),
                            metadataContext
                    );
                case SET:
                case SORTED_SET:
                case ORDERED_SET:
                    return new SetAttributeImpl(
                            new PluralAttributeBuilder<>(
                                    collectionJavaType,
                                    true,
                                    AttributeClassification.MANY_TO_MANY,
                                    classification,
                                    elementDomainType,
                                    null,
                                    MockEntityDomainType.this,
                                    property,
                                    null
                            ),
                            metadataContext
                    );
                case MAP:
                case SORTED_MAP:
                case ORDERED_MAP:
                    return new MapAttributeImpl(
                            new PluralAttributeBuilder<>(
                                    collectionJavaType,
                                    true,
                                    AttributeClassification.MANY_TO_MANY,
                                    classification,
                                    elementDomainType,
                                    unknownType, //TODO: get the key type from the persister
                                    MockEntityDomainType.this,
                                    property,
                                    null
                            ),
                            metadataContext
                    );
                default:
                    return null;
            }
        }

        private EmbeddableTypeImpl<Object> createEmbeddableDomainType(String entityName, CompositeType compositeType) {
            return new EmbeddableTypeImpl<Object>(new UnknownBasicJavaType<>(Object.class), true, metamodel.getJpaMetamodel()) {
                @Override
                public PersistentAttribute<Object, Object> findAttribute(String name) {
                    int i = compositeType.getPropertyIndex(name);
                    Type subtype = compositeType.getSubtypes()[i];
                    return createAttribute(
                            name,
                            entityName, //TOOD: WRONG!!!
                            subtype
                    );
                }
            };
        }
    }
}
