package org.hibernate.query.validator;

import org.hibernate.*;
import org.hibernate.boot.model.naming.ImplicitNamingStrategy;
import org.hibernate.boot.model.naming.ImplicitNamingStrategyJpaCompliantImpl;
import org.hibernate.boot.registry.BootstrapServiceRegistry;
import org.hibernate.boot.registry.BootstrapServiceRegistryBuilder;
import org.hibernate.boot.registry.internal.StandardServiceRegistryImpl;
import org.hibernate.boot.spi.SessionFactoryOptions;
import org.hibernate.cache.internal.DisabledCaching;
import org.hibernate.cache.internal.RegionFactoryInitiator;
import org.hibernate.cache.spi.CacheImplementor;
import org.hibernate.cfg.Settings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.hibernate.dialect.function.SQLFunctionRegistry;
import org.hibernate.engine.config.internal.ConfigurationServiceInitiator;
import org.hibernate.engine.jdbc.connections.internal.ConnectionProviderInitiator;
import org.hibernate.engine.jdbc.env.internal.JdbcEnvironmentInitiator;
import org.hibernate.engine.jdbc.internal.JdbcServicesInitiator;
import org.hibernate.engine.jdbc.spi.JdbcServices;
import org.hibernate.engine.profile.FetchProfile;
import org.hibernate.engine.query.spi.QueryPlanCache;
import org.hibernate.engine.spi.FilterDefinition;
import org.hibernate.engine.spi.SessionBuilderImplementor;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.graph.spi.RootGraphImplementor;
import org.hibernate.id.IdentifierGenerator;
import org.hibernate.id.factory.IdentifierGeneratorFactory;
import org.hibernate.internal.TypeLocatorImpl;
import org.hibernate.metadata.ClassMetadata;
import org.hibernate.metadata.CollectionMetadata;
import org.hibernate.metamodel.internal.MetamodelImpl;
import org.hibernate.metamodel.spi.MetamodelImplementor;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.proxy.EntityNotFoundDelegate;
import org.hibernate.query.spi.NamedQueryRepository;
import org.hibernate.service.spi.ServiceRegistryImplementor;
import org.hibernate.stat.spi.StatisticsImplementor;
import org.hibernate.type.Type;
import org.hibernate.type.TypeFactory;
import org.hibernate.type.TypeResolver;
import org.hibernate.type.spi.TypeConfiguration;

import javax.naming.NamingException;
import javax.naming.Reference;
import javax.persistence.*;
import javax.persistence.Query;
import javax.persistence.criteria.CriteriaBuilder;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Arrays.asList;
import static java.util.Collections.*;

abstract class MockSessionFactory implements SessionFactoryImplementor {

    static final SessionFactoryOptions options = new MockSessionFactoryOptions();

    static final TypeConfiguration typeConfiguration = new TypeConfiguration();

    static final TypeResolver typeResolver =
            new TypeResolver(typeConfiguration,
                    new TypeFactory(typeConfiguration));

    static final TypeHelper typeHelper = new TypeLocatorImpl(typeResolver);

    static final BootstrapServiceRegistry bootstrapServiceRegistry =
            new BootstrapServiceRegistryBuilder()
                    .applyStrategySelector(ImplicitNamingStrategy.class, "default",
                            ImplicitNamingStrategyJpaCompliantImpl.class)
                    .build();

    static final StandardServiceRegistryImpl serviceRegistry =
            new StandardServiceRegistryImpl(bootstrapServiceRegistry,
                    asList(GenericDialectFactoryInitiator.INSTANCE,
                            ConfigurationServiceInitiator.INSTANCE,
                            RegionFactoryInitiator.INSTANCE,
                            JdbcServicesInitiator.INSTANCE,
                            JdbcEnvironmentInitiator.INSTANCE,
                            ConnectionProviderInitiator.INSTANCE),
                    emptyList(),
                    emptyMap());

    private final JdbcServices jdbcServices =
            serviceRegistry.getService(JdbcServices.class);

    private final MetamodelImpl metamodel =
            new MetamodelImpl(MockSessionFactory.this, typeConfiguration) {
                @Override
                public String getImportedClassName(String className) {
                    return className;
                }

                @Override
                public EntityPersister entityPersister(String entityName)
                        throws MappingException {
                    return createMockEntityPersister(entityName);
                }

                @Override
                public EntityPersister locateEntityPersister(String entityName)
                        throws MappingException {
                    return createMockEntityPersister(entityName);
                }
            };

    /**
     * Lazily create a {@link MockEntityPersister}
     */
    abstract EntityPersister createMockEntityPersister(String entityName);

    @Override
    public JdbcServices getJdbcServices() {
        return jdbcServices;
    }

    @Override
    public TypeResolver getTypeResolver() {
        return typeResolver;
    }

    @Override
    public Type getIdentifierType(String className)
            throws MappingException {
        return createMockEntityPersister(className)
                .getIdentifierType();
    }

    @Override
    public String getIdentifierPropertyName(String className)
            throws MappingException {
        return createMockEntityPersister(className)
                .getIdentifierPropertyName();
    }

    @Override
    public Type getReferencedPropertyType(String className, String propertyName)
            throws MappingException {
        return createMockEntityPersister(className)
                .getPropertyType(propertyName);
    }

    @Override
    public MetamodelImplementor getMetamodel() {
        return metamodel;
    }

    @Override
    public ServiceRegistryImplementor getServiceRegistry() {
        return serviceRegistry;
    }

    @Override
    public String getUuid() {
        return options.getUuid();
    }

    @Override
    public String getName() {
        return options.getSessionFactoryName();
    }

    @Override
    public SessionFactoryOptions getSessionFactoryOptions() {
        return options;
    }

    @Override
    public Settings getSettings() {
        return new Settings(options);
    }

    @Override
    public Set getDefinedFilterNames() {
        return emptySet();
    }

    @Override
    public Type resolveParameterBindType(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Type resolveParameterBindType(Class aClass) {
        throw new UnsupportedOperationException();
    }

    @Override
    public IdentifierGeneratorFactory getIdentifierGeneratorFactory() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Reference getReference() throws NamingException {
        throw new UnsupportedOperationException();
    }

    @Override
    public SessionBuilderImplementor withOptions() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Session openSession() throws HibernateException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Session getCurrentSession() throws HibernateException {
        throw new UnsupportedOperationException();
    }

    @Override
    public StatelessSessionBuilder withStatelessOptions() {
        throw new UnsupportedOperationException();
    }

    @Override
    public StatelessSession openStatelessSession() {
        throw new UnsupportedOperationException();
    }

    @Override
    public StatelessSession openStatelessSession(Connection connection) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Session openTemporarySession() throws HibernateException {
        throw new UnsupportedOperationException();
    }

    @Override
    public CacheImplementor getCache() {
        return new DisabledCaching(this);
    }

    @Override
    public PersistenceUnitUtil getPersistenceUnitUtil() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addNamedQuery(String s, Query query) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T unwrap(Class<T> aClass) {
        throw new UnsupportedOperationException();
    }

    @Override
    public FilterDefinition getFilterDefinition(String s) throws HibernateException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsFetchProfileDefinition(String s) {
        return false;
    }

    @Override
    public TypeHelper getTypeHelper() {
        return typeHelper;
    }

    @Override
    public EntityNotFoundDelegate getEntityNotFoundDelegate() {
        return options.getEntityNotFoundDelegate();
    }

    @Override
    public CustomEntityDirtinessStrategy getCustomEntityDirtinessStrategy() {
        return options.getCustomEntityDirtinessStrategy();
    }

    @Override
    public CurrentTenantIdentifierResolver getCurrentTenantIdentifierResolver() {
        return options.getCurrentTenantIdentifierResolver();
    }

    @Override
    public SQLFunctionRegistry getSqlFunctionRegistry() {
        return new SQLFunctionRegistry(getJdbcServices().getDialect(),
                options.getCustomSqlFunctionMap());
    }

    @Override
    public ClassMetadata getClassMetadata(Class aClass) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ClassMetadata getClassMetadata(String s) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CollectionMetadata getCollectionMetadata(String s) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Map<String, ClassMetadata> getAllClassMetadata() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Map getAllCollectionMetadata() {
        throw new UnsupportedOperationException();
    }

    @Override
    public StatisticsImplementor getStatistics() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void close() {}

    @Override
    public Map<String, Object> getProperties() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isClosed() {
        return false;
    }

    @Override
    public Interceptor getInterceptor() {
        throw new UnsupportedOperationException();
    }

    @Override
    public QueryPlanCache getQueryPlanCache() {
        throw new UnsupportedOperationException();
    }

    @Override
    public NamedQueryRepository getNamedQueryRepository() {
        throw new UnsupportedOperationException();
    }

    @Override
    public FetchProfile getFetchProfile(String s) {
        throw new UnsupportedOperationException();
    }

    @Override
    public IdentifierGenerator getIdentifierGenerator(String s) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addObserver(SessionFactoryObserver sessionFactoryObserver) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DeserializationResolver getDeserializationResolver() {
        throw new UnsupportedOperationException();
    }

    @Override
    public EntityManager createEntityManager() {
        throw new UnsupportedOperationException();
    }

    @Override
    public EntityManager createEntityManager(Map map) {
        throw new UnsupportedOperationException();
    }

    @Override
    public EntityManager createEntityManager(SynchronizationType synchronizationType) {
        throw new UnsupportedOperationException();
    }

    @Override
    public EntityManager createEntityManager(SynchronizationType synchronizationType, Map map) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CriteriaBuilder getCriteriaBuilder() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isOpen() {
        return true;
    }

    @Override
    public <T> void addNamedEntityGraph(String s, EntityGraph<T> entityGraph) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> List<RootGraphImplementor<? super T>> findEntityGraphsByJavaType(Class<T> aClass) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RootGraphImplementor<?> findEntityGraphByName(String s) {
        throw new UnsupportedOperationException();
    }

}
