package org.hibernate.query.validator;

import org.hibernate.HibernateException;
import org.hibernate.dialect.Dialect;
import org.hibernate.engine.jdbc.dialect.internal.DialectFactoryInitiator;
import org.hibernate.engine.jdbc.dialect.spi.DialectFactory;
import org.hibernate.engine.jdbc.dialect.spi.DialectResolutionInfoSource;
import org.hibernate.service.spi.ServiceRegistryImplementor;

import java.util.Map;

class GenericDialect extends Dialect {}

class GenericDialectFactoryInitiator extends DialectFactoryInitiator {

    static final DialectFactoryInitiator INSTANCE = new GenericDialectFactoryInitiator();

    @Override
    public DialectFactory initiateService(Map configurationValues,
                                          ServiceRegistryImplementor registry) {
        return new DialectFactory() {
            @Override
            public Dialect buildDialect(Map configValues,
                                        DialectResolutionInfoSource resolutionInfoSource)
                    throws HibernateException {
                return new GenericDialect();
            }
        };
    }
}
