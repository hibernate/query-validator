package org.hibernate.query.validator;

import org.hibernate.boot.registry.StandardServiceInitiator;
import org.hibernate.dialect.Dialect;
import org.hibernate.engine.jdbc.internal.JdbcServicesImpl;
import org.hibernate.engine.jdbc.spi.JdbcServices;
import org.hibernate.service.spi.ServiceRegistryImplementor;

import java.util.Map;

//class MockJdbcServicesInitiator implements StandardServiceInitiator<JdbcServices> {
//
//
//    private static Dialect defaultDialect;
//
//    static final MockJdbcServicesInitiator INSTANCE
//            = new MockJdbcServicesInitiator();
//
//    @Override
//    public Class<JdbcServices> getServiceInitiated() {
//        return JdbcServices.class;
//    }
//
//    @Override
//    public JdbcServices initiateService(Map configurationValues,
//                                        ServiceRegistryImplementor registry) {
//        return new JdbcServicesImpl() {
//            @Override
//            public Dialect getDialect() {
//                Dialect dialect = super.getDialect();
//                if (dialect != null) {
//                    return dialect;
//                }
//                if (defaultDialect == null) {
//                    defaultDialect = new GenericDialect();
//                }
//                return defaultDialect;
//            }
//        };
//    }
//}
