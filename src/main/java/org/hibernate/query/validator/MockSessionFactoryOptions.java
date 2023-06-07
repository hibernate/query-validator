package org.hibernate.query.validator;

import org.hibernate.EntityNameResolver;
import org.hibernate.SessionFactoryObserver;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.spi.SessionFactoryOptions;
import org.hibernate.jpa.spi.JpaCompliance;
import org.hibernate.loader.BatchFetchStyle;

/**
 * @author Gavin King
 */
public abstract class MockSessionFactoryOptions implements SessionFactoryOptions {

    private static final SessionFactoryObserver[] NO_OBSERVERS = new SessionFactoryObserver[0];
    private static final EntityNameResolver[] NO_RESOLVERS = new EntityNameResolver[0];

    private static JpaCompliance jpaCompliance = Mocker.nullary(JpaCompliance.class).get();

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
    public StandardServiceRegistry getServiceRegistry() {
        return MockSessionFactory.serviceRegistry;
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

}
