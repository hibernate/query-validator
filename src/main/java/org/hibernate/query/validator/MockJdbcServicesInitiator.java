package org.hibernate.query.validator;

import org.hibernate.dialect.Dialect;
import org.hibernate.engine.jdbc.internal.JdbcServicesInitiator;
import org.hibernate.engine.jdbc.spi.JdbcServices;
import org.hibernate.service.spi.ServiceRegistryImplementor;

import java.util.Map;

/**
 * @author Gavin King
 */
class MockJdbcServicesInitiator extends JdbcServicesInitiator {

    static final JdbcServicesInitiator INSTANCE = new MockJdbcServicesInitiator();

    private static final JdbcServices jdbcServices = Mocker.nullary(MockJdbcServices.class).get();
    private static final GenericDialect genericDialect = new GenericDialect();

    public abstract static class MockJdbcServices implements JdbcServices {
        @Override
        public Dialect getDialect() {
            //TODO: Are there advantages to using the
            //      configured dialect if any?
            //      Add dialect argument to @CheckHQL!
            return genericDialect;
        }
    }

    @Override
    public JdbcServices initiateService(Map configurationValues,
                                        ServiceRegistryImplementor registry) {
        return jdbcServices;
    }
}