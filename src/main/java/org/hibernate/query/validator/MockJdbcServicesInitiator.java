package org.hibernate.query.validator;

import org.hibernate.dialect.Dialect;
import org.hibernate.engine.jdbc.LobCreationContext;
import org.hibernate.engine.jdbc.LobCreator;
import org.hibernate.engine.jdbc.connections.spi.JdbcConnectionAccess;
import org.hibernate.engine.jdbc.env.spi.ExtractedDatabaseMetaData;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.hibernate.engine.jdbc.internal.JdbcServicesInitiator;
import org.hibernate.engine.jdbc.spi.JdbcServices;
import org.hibernate.engine.jdbc.spi.ResultSetWrapper;
import org.hibernate.engine.jdbc.spi.SqlExceptionHelper;
import org.hibernate.engine.jdbc.spi.SqlStatementLogger;
import org.hibernate.service.spi.ServiceRegistryImplementor;

import java.util.Map;

class MockJdbcServicesInitiator extends JdbcServicesInitiator {

    static final JdbcServicesInitiator INSTANCE = new MockJdbcServicesInitiator();

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

    private static final JdbcServices JDBC_SERVICES = Mocker.make(MockJdbcServices.class);

    @Override
    public JdbcServices initiateService(Map configurationValues,
                                        ServiceRegistryImplementor registry) {
        return JDBC_SERVICES;
    }
}