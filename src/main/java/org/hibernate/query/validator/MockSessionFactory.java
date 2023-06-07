package org.hibernate.query.validator;

import org.hibernate.CustomEntityDirtinessStrategy;
import org.hibernate.MappingException;
import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.registry.BootstrapServiceRegistryBuilder;
import org.hibernate.boot.registry.internal.StandardServiceRegistryImpl;
import org.hibernate.boot.spi.SessionFactoryOptions;
import org.hibernate.cache.internal.DisabledCaching;
import org.hibernate.cache.spi.CacheImplementor;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.hibernate.dialect.Dialect;
import org.hibernate.engine.jdbc.spi.JdbcServices;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.graph.spi.RootGraphImplementor;
import org.hibernate.internal.FastSessionServices;
import org.hibernate.metamodel.model.domain.internal.MappingMetamodelImpl;
import org.hibernate.metamodel.spi.MetamodelImplementor;
import org.hibernate.persister.collection.CollectionPersister;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.proxy.EntityNotFoundDelegate;
import org.hibernate.query.sqm.function.SqmFunctionRegistry;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.service.spi.ServiceRegistryImplementor;
import org.hibernate.type.*;
import org.hibernate.type.descriptor.jdbc.JdbcTypeIndicators;
import org.hibernate.type.spi.TypeConfiguration;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.Collections.*;

/**
 * @author Gavin King
 */
public abstract class MockSessionFactory implements SessionFactoryImplementor {

    private static final SessionFactoryOptions options = Mocker.nullary(MockSessionFactoryOptions.class).get();

    private final Map<String,MockEntityPersister> entityPersistersByName = new HashMap<>();
    private final Map<String,MockCollectionPersister> collectionPersistersByName = new HashMap<>();

    static final TypeConfiguration typeConfiguration = new TypeConfiguration() {
        @Override
        public JdbcTypeIndicators getCurrentBaseSqlTypeIndicators() {
            return new JdbcTypeIndicators() {
                @Override
                public TypeConfiguration getTypeConfiguration() {
                    return typeConfiguration;
                }

                @Override
                public Dialect getDialect() {
                    return new GenericDialect();
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
            };
        }
    };

    static final SqmFunctionRegistry functionRegistry = new SqmFunctionRegistry();
    static {
        new GenericDialect().initializeFunctionRegistry(new FunctionContributions() {
            @Override
            public SqmFunctionRegistry getFunctionRegistry() {
                return MockSessionFactory.functionRegistry;
            }

            @Override
            public TypeConfiguration getTypeConfiguration() {
                return MockSessionFactory.typeConfiguration;
            }

            @Override
            public ServiceRegistry getServiceRegistry() {
                return MockSessionFactory.serviceRegistry;
            }
        });
    }

    static final StandardServiceRegistryImpl serviceRegistry =
            new StandardServiceRegistryImpl(
                    new BootstrapServiceRegistryBuilder().build(),
                    singletonList(MockJdbcServicesInitiator.INSTANCE),
                    emptyList(),
                    emptyMap());

    private final MetamodelImplementor metamodel =
            new MappingMetamodelImpl(typeConfiguration, serviceRegistry) {
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
                public CollectionPersister collectionPersister(String role) {
                    return createCollectionPersister(role);
                }
            };

    public MockSessionFactory() {
    }

    @Override
    public TypeConfiguration getTypeConfiguration() {
        return typeConfiguration;
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

    abstract boolean isClassDefined(String qualifiedName);

    abstract boolean isFieldDefined(String qualifiedClassName, String fieldName);

    abstract boolean isConstructorDefined(String qualifiedClassName, List<Type> argumentTypes);

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
    public ServiceRegistryImplementor getServiceRegistry() {
        return serviceRegistry;
    }

    @Override
    public JdbcServices getJdbcServices() {
        return serviceRegistry.getService(JdbcServices.class);
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
    public Set<String> getDefinedFilterNames() {
        return emptySet();
    }

    @Override
    public CacheImplementor getCache() {
        return new DisabledCaching(this);
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

}
