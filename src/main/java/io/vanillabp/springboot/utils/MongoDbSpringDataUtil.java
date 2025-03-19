package io.vanillabp.springboot.utils;

import io.vanillabp.springboot.adapter.SpringDataUtil;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.context.ApplicationContext;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.convert.DbRefResolver;
import org.springframework.data.mongodb.core.convert.DefaultDbRefResolver;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.core.mapping.MongoPersistentEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.repository.support.Repositories;
import org.springframework.lang.Nullable;

public class MongoDbSpringDataUtil implements SpringDataUtil {

    private static final Map<Class<?>, MongoRepository<?, Object>> REPOSITORY_MAP = new HashMap<>();

    private static final Map<Class<?>, MongoPersistentEntity<?>> PERSISTENT_ENTITY_MAP_MAP = new HashMap<>();

    private final ApplicationContext applicationContext;

    private final MongoConverter mongoConverter;

    public MongoDbSpringDataUtil(
            final ApplicationContext applicationContext,
            final MongoDatabaseFactory mongoDbFactory,
            @Nullable final MongoConverter mongoConverter) {

        this.applicationContext = applicationContext;
        this.mongoConverter = mongoConverter == null ? getDefaultMongoConverter(mongoDbFactory) : mongoConverter;

    }

    @Override
    @SuppressWarnings("unchecked")
    public <O> MongoRepository<? super O, Object> getRepository(
        final O object) {

        //noinspection unchecked
        return getRepository((Class<O>) object.getClass());

    }

    @Override
    @SuppressWarnings("unchecked")
    public <O> MongoRepository<O, Object> getRepository(
            final Class<O> type) {

        Class<? super O> cls = type;

        if (REPOSITORY_MAP.containsKey(cls)) {
            return (MongoRepository<O, Object>) REPOSITORY_MAP.get(cls);
        }

        var repositories = new Repositories(applicationContext);

        Optional<Object> repository;
        do {
            repository = repositories.getRepositoryFor(cls);
            cls = repository.isPresent() ? cls : cls.getSuperclass();
        } while (repository.isEmpty() && (cls != Object.class));

        if (repository.isEmpty()) {
            throw new IllegalStateException(
                String.format("No Spring Data repository defined for '%s'!", type.getName()));
        }

        REPOSITORY_MAP.put(cls, (MongoRepository<?, Object>) repository.get());

        return (MongoRepository<O, Object>) repository.get();

    }

    private MongoPersistentEntity<?> getPersistentEntity(
            final Class<?> type) {

        if (PERSISTENT_ENTITY_MAP_MAP.containsKey(type)) {
            return PERSISTENT_ENTITY_MAP_MAP.get(type);
        }

        final var persistentEntity = mongoConverter.getMappingContext().getPersistentEntity(type);
        if (persistentEntity == null) {
            throw new RuntimeException("Class '"
                    + type.getName()
                    + "' is not an entity known to MongoDb! Maybe you did not place the "
                    + "@org.springframework.data.mongodb.core.mapping.Document annotation at class level?");
        }
        if (!persistentEntity.hasIdProperty()) {
            throw new RuntimeException("There is no property or getter annotated with "
                    + "@org.springframework.data.annotation.Id in class '"
                    + type.getName()
                    + "' or its superclasses!");
        }

        PERSISTENT_ENTITY_MAP_MAP.put(type, persistentEntity);

        return persistentEntity;

    }

    @Override
    public Class<?> getIdType(
        final Class<?> type) {

        final var persistentEntity = getPersistentEntity(type);
        return Objects.requireNonNull(persistentEntity.getIdProperty()).getType();

    }

    public String getIdName(
            final Class<?> type) {

        final var persistentEntity = getPersistentEntity(type);
        return Objects.requireNonNull(persistentEntity.getIdProperty()).getName();

    }

    @Override
    @SuppressWarnings("unchecked")
    public <I> I getId(
            final Object entity) {

        final var persistentEntity = getPersistentEntity(entity.getClass());
        return (I) Objects.requireNonNull(persistentEntity.getIdentifierAccessor(entity).getIdentifier());

    }

    @Override
    public <O> O unproxy(
            final O entity) {

        throw new UnsupportedOperationException();

    }

    @Override
    public <O> boolean isPersistedEntity(
            final Class<O> entityClass,
            final O entity) {

        final var persistentEntity = getPersistentEntity(entityClass);
        return persistentEntity.isNew(entity);

    }

    /**
     * @see "MongoTemplate#getDefaultMongoConverter(MongoDatabaseFactory)"
     */
    private static MongoConverter getDefaultMongoConverter(
            final MongoDatabaseFactory factory) {

        DbRefResolver dbRefResolver = new DefaultDbRefResolver(factory);
        MongoCustomConversions conversions = new MongoCustomConversions(Collections.emptyList());
        MongoMappingContext mappingContext = new MongoMappingContext();
        mappingContext.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
        mappingContext.afterPropertiesSet();
        MappingMongoConverter converter = new MappingMongoConverter(dbRefResolver, mappingContext);
        converter.setCustomConversions(conversions);
        converter.setCodecRegistryProvider(factory);
        converter.afterPropertiesSet();
        return converter;

    }

}
