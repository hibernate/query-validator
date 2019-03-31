package org.hibernate.query.validator;

import org.hibernate.*;
import org.hibernate.boot.SchemaAutoTooling;
import org.hibernate.boot.TempTableDdlTransactionHandling;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.spi.SessionFactoryOptions;
import org.hibernate.cache.spi.TimestampsCacheFactory;
import org.hibernate.cfg.BaselineSessionEventsListenerBuilder;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.hibernate.dialect.function.SQLFunction;
import org.hibernate.hql.spi.id.MultiTableBulkIdStrategy;
import org.hibernate.jpa.spi.JpaCompliance;
import org.hibernate.loader.BatchFetchStyle;
import org.hibernate.proxy.EntityNotFoundDelegate;
import org.hibernate.resource.jdbc.spi.PhysicalConnectionHandlingMode;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.hibernate.tuple.entity.EntityTuplizerFactory;

import java.util.Map;
import java.util.TimeZone;

import static java.util.Collections.emptyMap;

class MockSessionFactoryOptions implements SessionFactoryOptions {

    private static final SessionFactoryObserver[] NO_OBSERVERS = new SessionFactoryObserver[0];
    private static final EntityNameResolver[] NO_RESOLVERS = new EntityNameResolver[0];

    static final SessionFactoryOptions OPTIONS = new MockSessionFactoryOptions();

    private static final JpaCompliance jpaCompliance = new JpaCompliance() {
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

    @Override
    public String getSessionFactoryName() {
        return "mock";
    }

    @Override
    public String getUuid() {
        return "mock";
    }

    @Override
    public JpaCompliance getJpaCompliance() {
        return jpaCompliance;
    }

    @Override
    public EntityTuplizerFactory getEntityTuplizerFactory() {
        return new EntityTuplizerFactory();
    }

    @Override
    public StandardServiceRegistry getServiceRegistry() {
        return MockSessionFactory.serviceRegistry;
    }

    @Override
    public EntityMode getDefaultEntityMode() {
        return EntityMode.POJO;
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
    public Map<String, SQLFunction> getCustomSqlFunctionMap() {
        return emptyMap();
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
    public BatchFetchStyle getBatchFetchStyle() {
        return BatchFetchStyle.LEGACY;
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
        return NullPrecedence.NONE;
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
    public boolean isJtaTrackByThread() {
        return false;
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
    public String getCacheRegionPrefix() {
        return "";
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
    public boolean isCommentsEnabled() {
        return false;
    }

    @Override
    public void setCheckNullability(boolean enabled) {}

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
    public boolean jdbcStyleParamsZeroBased() {
        return false;
    }

    @Override
    public boolean isFailOnPaginationOverCollectionFetchEnabled() {
        return false;
    }

    @Override
    public TimeZone getJdbcTimeZone() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Map getQuerySubstitutions() {
        throw new UnsupportedOperationException();
    }

    @Override
    public TimestampsCacheFactory getTimestampsCacheFactory() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object getBeanManagerReference() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object getValidatorFactoryReference() {
        throw new UnsupportedOperationException();
    }

    @Override
    public MultiTableBulkIdStrategy getMultiTableBulkIdStrategy() {
        throw new UnsupportedOperationException();
    }

    @Override
    public TempTableDdlTransactionHandling getTempTableDdlTransactionHandling() {
        throw new UnsupportedOperationException();
    }

    @Override
    public PhysicalConnectionHandlingMode getPhysicalConnectionHandlingMode() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ConnectionReleaseMode getConnectionReleaseMode() {
        throw new UnsupportedOperationException();
    }

    @Override
    public CustomEntityDirtinessStrategy getCustomEntityDirtinessStrategy() {
        throw new UnsupportedOperationException();
    }

    @Override
    public EntityNotFoundDelegate getEntityNotFoundDelegate() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Class<? extends Interceptor> getStatelessInterceptorImplementor() {
        throw new UnsupportedOperationException();
    }

    @Override
    public BaselineSessionEventsListenerBuilder getBaselineSessionEventsListenerBuilder() {
        throw new UnsupportedOperationException();
    }

    @Override
    public StatementInspector getStatementInspector() {
        throw new UnsupportedOperationException();
    }

    @Override
    public MultiTenancyStrategy getMultiTenancyStrategy() {
        throw new UnsupportedOperationException();
    }

    @Override
    public CurrentTenantIdentifierResolver getCurrentTenantIdentifierResolver() {
        throw new UnsupportedOperationException();
    }

    @Override
    public SchemaAutoTooling getSchemaAutoTooling() {
        throw new UnsupportedOperationException();
    }

}
