package org.hibernate.query.validator;

import org.hibernate.*;
import org.hibernate.boot.SchemaAutoTooling;
import org.hibernate.boot.TempTableDdlTransactionHandling;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.internal.BootstrapServiceRegistryImpl;
import org.hibernate.boot.registry.internal.StandardServiceRegistryImpl;
import org.hibernate.boot.spi.SessionFactoryOptions;
import org.hibernate.cache.spi.CacheImplementor;
import org.hibernate.cache.spi.TimestampsCacheFactory;
import org.hibernate.cfg.BaselineSessionEventsListenerBuilder;
import org.hibernate.cfg.Settings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.HSQLDialect;
import org.hibernate.dialect.function.SQLFunction;
import org.hibernate.dialect.function.SQLFunctionRegistry;
import org.hibernate.engine.jdbc.internal.JdbcServicesImpl;
import org.hibernate.engine.jdbc.spi.JdbcServices;
import org.hibernate.engine.profile.FetchProfile;
import org.hibernate.engine.query.spi.QueryPlanCache;
import org.hibernate.engine.spi.FilterDefinition;
import org.hibernate.engine.spi.SessionBuilderImplementor;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.graph.spi.RootGraphImplementor;
import org.hibernate.hql.spi.id.MultiTableBulkIdStrategy;
import org.hibernate.id.IdentifierGenerator;
import org.hibernate.id.factory.IdentifierGeneratorFactory;
import org.hibernate.jpa.spi.JpaCompliance;
import org.hibernate.loader.BatchFetchStyle;
import org.hibernate.metadata.ClassMetadata;
import org.hibernate.metadata.CollectionMetadata;
import org.hibernate.metamodel.internal.MetamodelImpl;
import org.hibernate.metamodel.spi.MetamodelImplementor;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.proxy.EntityNotFoundDelegate;
import org.hibernate.query.spi.NamedQueryRepository;
import org.hibernate.resource.jdbc.spi.PhysicalConnectionHandlingMode;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.hibernate.service.internal.ProvidedService;
import org.hibernate.service.spi.ServiceRegistryImplementor;
import org.hibernate.stat.spi.StatisticsImplementor;
import org.hibernate.tuple.entity.EntityTuplizerFactory;
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
import java.util.TimeZone;

import static java.util.Collections.*;

class MockSessionFactory implements SessionFactoryImplementor {

    EntityPersister entityPersister(String entityName) {
        return null;
    }

    private static Dialect defaultDialect;

    @Override
    public JdbcServices getJdbcServices() {
        return new JdbcServicesImpl() {
            @Override
            public Dialect getDialect() {
                Dialect dialect = super.getDialect();
                if (dialect!=null) {
                    return dialect;
                }
                if (defaultDialect==null) {
                    defaultDialect = new HSQLDialect();
                }
                return defaultDialect;
            }
        };
    }

    @Override
    public TypeResolver getTypeResolver() {
        return new TypeResolver(null, new TypeFactory(null));
    }

    @Override
    public Type getIdentifierType(String s) throws MappingException {
        return entityPersister(s).getIdentifierType();
    }

    @Override
    public String getIdentifierPropertyName(String s) throws MappingException {
        return entityPersister(s).getIdentifierPropertyName();
    }

    @Override
    public Type getReferencedPropertyType(String s, String propertyName) throws MappingException {
        return entityPersister(s).getPropertyType(propertyName);
    }

    @Override
    public MetamodelImplementor getMetamodel() {
        return new MetamodelImpl(MockSessionFactory.this, new TypeConfiguration()) {
            @Override
            public String getImportedClassName(String className) {
                return className;
            }

            @Override
            public EntityPersister entityPersister(String entityName) throws MappingException {
                return MockSessionFactory.this.entityPersister(entityName);
            }
        };
    }

    private StandardServiceRegistryImpl serviceRegistry =
            new StandardServiceRegistryImpl(new BootstrapServiceRegistryImpl(),
                    emptyList(),
                    singletonList(new ProvidedService<>(JdbcServices.class, getJdbcServices())),
                    emptyMap());

    @Override
    public ServiceRegistryImplementor getServiceRegistry() {
        return serviceRegistry;
    }

    @Override
    public String getUuid() {
        return "dummy";
    }

    @Override
    public String getName() {
        return "dummy";
    }

    @Override
    public SessionFactoryOptions getSessionFactoryOptions() {
        return new SessionFactoryOptions() {
            @Override
            public String getUuid() {
                return MockSessionFactory.this.getUuid();
            }

            @Override
            public EntityTuplizerFactory getEntityTuplizerFactory() {
                return new EntityTuplizerFactory();
            }

            @Override
            public StandardServiceRegistry getServiceRegistry() {
                return serviceRegistry;
            }

            @Override
            public String getSessionFactoryName() {
                return getName();
            }

            @Override
            public EntityMode getDefaultEntityMode() {
                return EntityMode.POJO;
            }

            @Override
            public Object getBeanManagerReference() {
                return null;
            }

            @Override
            public Object getValidatorFactoryReference() {
                return null;
            }

            @Override
            public boolean isJpaBootstrap() {
                return false;
            }

            @Override
            public boolean isJtaTransactionAccessEnabled() {
                return false;
            }

            @Override
            public boolean isSessionFactoryNameAlsoJndiName() {
                return false;
            }

            @Override
            public boolean isFlushBeforeCompletionEnabled() {
                return false;
            }

            @Override
            public boolean isAutoCloseSessionEnabled() {
                return false;
            }

            @Override
            public boolean isStatisticsEnabled() {
                return false;
            }

            @Override
            public Interceptor getInterceptor() {
                return null;
            }

            @Override
            public Class<? extends Interceptor> getStatelessInterceptorImplementor() {
                return null;
            }

            @Override
            public StatementInspector getStatementInspector() {
                return null;
            }

            @Override
            public SessionFactoryObserver[] getSessionFactoryObservers() {
                return new SessionFactoryObserver[0];
            }

            @Override
            public BaselineSessionEventsListenerBuilder getBaselineSessionEventsListenerBuilder() {
                return null;
            }

            @Override
            public boolean isIdentifierRollbackEnabled() {
                return false;
            }

            @Override
            public boolean isCheckNullability() {
                return false;
            }

            @Override
            public boolean isInitializeLazyStateOutsideTransactionsEnabled() {
                return false;
            }

            @Override
            public MultiTableBulkIdStrategy getMultiTableBulkIdStrategy() {
                return null;
            }

            @Override
            public TempTableDdlTransactionHandling getTempTableDdlTransactionHandling() {
                return null;
            }

            @Override
            public BatchFetchStyle getBatchFetchStyle() {
                return null;
            }

            @Override
            public boolean isDelayBatchFetchLoaderCreationsEnabled() {
                return false;
            }

            @Override
            public int getDefaultBatchFetchSize() {
                return 0;
            }

            @Override
            public Integer getMaximumFetchDepth() {
                return null;
            }

            @Override
            public NullPrecedence getDefaultNullPrecedence() {
                return null;
            }

            @Override
            public boolean isOrderUpdatesEnabled() {
                return false;
            }

            @Override
            public boolean isOrderInsertsEnabled() {
                return false;
            }

            @Override
            public MultiTenancyStrategy getMultiTenancyStrategy() {
                return null;
            }

            @Override
            public CurrentTenantIdentifierResolver getCurrentTenantIdentifierResolver() {
                return null;
            }

            @Override
            public boolean isJtaTrackByThread() {
                return false;
            }

            @Override
            public Map getQuerySubstitutions() {
                return null;
            }

            @Override
            public boolean isNamedQueryStartupCheckingEnabled() {
                return false;
            }

            @Override
            public boolean isConventionalJavaConstants() {
                return true;
            }

            @Override
            public boolean isSecondLevelCacheEnabled() {
                return false;
            }

            @Override
            public boolean isQueryCacheEnabled() {
                return false;
            }

            @Override
            public TimestampsCacheFactory getTimestampsCacheFactory() {
                return null;
            }

            @Override
            public String getCacheRegionPrefix() {
                return null;
            }

            @Override
            public boolean isMinimalPutsEnabled() {
                return false;
            }

            @Override
            public boolean isStructuredCacheEntriesEnabled() {
                return false;
            }

            @Override
            public boolean isDirectReferenceCacheEntriesEnabled() {
                return false;
            }

            @Override
            public boolean isAutoEvictCollectionCache() {
                return false;
            }

            @Override
            public SchemaAutoTooling getSchemaAutoTooling() {
                return null;
            }

            @Override
            public int getJdbcBatchSize() {
                return 0;
            }

            @Override
            public boolean isJdbcBatchVersionedData() {
                return false;
            }

            @Override
            public boolean isScrollableResultSetsEnabled() {
                return false;
            }

            @Override
            public boolean isWrapResultSetsEnabled() {
                return false;
            }

            @Override
            public boolean isGetGeneratedKeysEnabled() {
                return false;
            }

            @Override
            public Integer getJdbcFetchSize() {
                return null;
            }

            @Override
            public PhysicalConnectionHandlingMode getPhysicalConnectionHandlingMode() {
                return null;
            }

            @Override
            public ConnectionReleaseMode getConnectionReleaseMode() {
                return null;
            }

            @Override
            public boolean isCommentsEnabled() {
                return false;
            }

            @Override
            public CustomEntityDirtinessStrategy getCustomEntityDirtinessStrategy() {
                return null;
            }

            @Override
            public EntityNameResolver[] getEntityNameResolvers() {
                return new EntityNameResolver[0];
            }

            @Override
            public EntityNotFoundDelegate getEntityNotFoundDelegate() {
                return null;
            }

            @Override
            public Map<String, SQLFunction> getCustomSqlFunctionMap() {
                return null;
            }

            @Override
            public void setCheckNullability(boolean b) {}

            @Override
            public boolean isPreferUserTransaction() {
                return false;
            }

            @Override
            public boolean isProcedureParameterNullPassingEnabled() {
                return false;
            }

            @Override
            public boolean isCollectionJoinSubqueryRewriteEnabled() {
                return false;
            }

            @Override
            public boolean isAllowOutOfTransactionUpdateOperations() {
                return false;
            }

            @Override
            public boolean isReleaseResourcesOnCloseEnabled() {
                return false;
            }

            @Override
            public TimeZone getJdbcTimeZone() {
                return null;
            }

            @Override
            public boolean jdbcStyleParamsZeroBased() {
                return false;
            }

            @Override
            public JpaCompliance getJpaCompliance() {
                return new JpaCompliance() {

                    @Override
                    public boolean isJpaQueryComplianceEnabled() {
                        return false;
                    }

                    @Override
                    public boolean isJpaTransactionComplianceEnabled() {
                        return false;
                    }

                    @Override
                    public boolean isJpaListComplianceEnabled() {
                        return false;
                    }

                    @Override
                    public boolean isJpaClosedComplianceEnabled() {
                        return false;
                    }

                    @Override
                    public boolean isJpaProxyComplianceEnabled() {
                        return false;
                    }

                    @Override
                    public boolean isJpaCacheComplianceEnabled() {
                        return false;
                    }

                    @Override
                    public boolean isGlobalGeneratorScopeEnabled() {
                        return false;
                    }
                };
            }

            @Override
            public boolean isFailOnPaginationOverCollectionFetchEnabled() {
                return false;
            }
        };
    }

    @Override
    public Type resolveParameterBindType(Object o) {
        return null;
    }

    @Override
    public Type resolveParameterBindType(Class aClass) {
        return null;
    }

    @Override
    public IdentifierGeneratorFactory getIdentifierGeneratorFactory() {
        return null;
    }

    @Override
    public Reference getReference() throws NamingException {
        return null;
    }

    @Override
    public SessionBuilderImplementor withOptions() {
        return null;
    }

    @Override
    public Session openSession() throws HibernateException {
        return null;
    }

    @Override
    public Session getCurrentSession() throws HibernateException {
        return null;
    }

    @Override
    public StatelessSessionBuilder withStatelessOptions() {
        return null;
    }

    @Override
    public StatelessSession openStatelessSession() {
        return null;
    }

    @Override
    public StatelessSession openStatelessSession(Connection connection) {
        return null;
    }

    @Override
    public Session openTemporarySession() throws HibernateException {
        return null;
    }

    @Override
    public CacheImplementor getCache() {
        return null;
    }

    @Override
    public PersistenceUnitUtil getPersistenceUnitUtil() {
        return null;
    }

    @Override
    public void addNamedQuery(String s, Query query) {}

    @Override
    public <T> T unwrap(Class<T> aClass) {
        return null;
    }

    @Override
    public <T> void addNamedEntityGraph(String s, EntityGraph<T> entityGraph) {}

    @Override
    public Set getDefinedFilterNames() {
        return null;
    }

    @Override
    public FilterDefinition getFilterDefinition(String s) throws HibernateException {
        return null;
    }

    @Override
    public boolean containsFetchProfileDefinition(String s) {
        return false;
    }

    @Override
    public TypeHelper getTypeHelper() {
        return null;
    }

    @Override
    public ClassMetadata getClassMetadata(Class aClass) {
        return null;
    }

    @Override
    public ClassMetadata getClassMetadata(String s) {
        return null;
    }

    @Override
    public CollectionMetadata getCollectionMetadata(String s) {
        return null;
    }

    @Override
    public Map<String, ClassMetadata> getAllClassMetadata() {
        return null;
    }

    @Override
    public Map getAllCollectionMetadata() {
        return null;
    }

    @Override
    public StatisticsImplementor getStatistics() {
        return null;
    }

    @Override
    public void close() throws HibernateException {}

    @Override
    public Map<String, Object> getProperties() {
        return null;
    }

    @Override
    public boolean isClosed() {
        return false;
    }

    @Override
    public Interceptor getInterceptor() {
        return null;
    }

    @Override
    public QueryPlanCache getQueryPlanCache() {
        return null;
    }

    @Override
    public NamedQueryRepository getNamedQueryRepository() {
        return null;
    }

    @Override
    public FetchProfile getFetchProfile(String s) {
        return null;
    }

    @Override
    public IdentifierGenerator getIdentifierGenerator(String s) {
        return null;
    }

    @Override
    public EntityNotFoundDelegate getEntityNotFoundDelegate() {
        return null;
    }

    @Override
    public SQLFunctionRegistry getSqlFunctionRegistry() {
        return null;
    }

    @Override
    public void addObserver(SessionFactoryObserver sessionFactoryObserver) {}

    @Override
    public CustomEntityDirtinessStrategy getCustomEntityDirtinessStrategy() {
        return null;
    }

    @Override
    public CurrentTenantIdentifierResolver getCurrentTenantIdentifierResolver() {
        return null;
    }

    @Override
    public DeserializationResolver getDeserializationResolver() {
        return null;
    }

    @Override
    public Settings getSettings() {
        return null;
    }

    @Override
    public EntityManager createEntityManager() {
        return null;
    }

    @Override
    public EntityManager createEntityManager(Map map) {
        return null;
    }

    @Override
    public EntityManager createEntityManager(SynchronizationType synchronizationType) {
        return null;
    }

    @Override
    public EntityManager createEntityManager(SynchronizationType synchronizationType, Map map) {
        return null;
    }

    @Override
    public CriteriaBuilder getCriteriaBuilder() {
        return null;
    }

    @Override
    public boolean isOpen() {
        return true;
    }

    @Override
    public <T> List<RootGraphImplementor<? super T>> findEntityGraphsByJavaType(Class<T> aClass) {
        return null;
    }

    @Override
    public RootGraphImplementor<?> findEntityGraphByName(String s) {
        return null;
    }
}
