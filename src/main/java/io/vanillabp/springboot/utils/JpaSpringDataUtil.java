package io.vanillabp.springboot.utils;

import io.vanillabp.springboot.adapter.SpringDataUtil;
import jakarta.persistence.Id;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.hibernate.Hibernate;
import org.springframework.context.ApplicationContext;
import org.springframework.data.jpa.repository.JpaContext;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.core.EntityInformation;
import org.springframework.data.repository.support.Repositories;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;

public class JpaSpringDataUtil implements SpringDataUtil {

    private static final Map<Class<?>, JpaRepository<?, Object>> REPOSITORY_MAP = new HashMap<>();
    
    private static final Map<Class<?>, EntityInformation<?, Object>> ENTITYINFO_MAP = new HashMap<>();
    
    private final ApplicationContext applicationContext;

    private final LocalContainerEntityManagerFactoryBean containerEntityManagerFactoryBean;
    
    private final JpaContext jpaContext;
    
    public JpaSpringDataUtil(
            final ApplicationContext applicationContext,
            final JpaContext jpaContext,
            final LocalContainerEntityManagerFactoryBean containerEntityManagerFactoryBean) {
        
        this.applicationContext = applicationContext;
        this.jpaContext = jpaContext;
        this.containerEntityManagerFactoryBean = containerEntityManagerFactoryBean;
        
    }

    @SuppressWarnings("unchecked")
    public <O> JpaRepository<? super O, Object> getRepository(
            final O object) {

        //noinspection unchecked
        return getRepository((Class<O>) object.getClass());

    }

    @SuppressWarnings("unchecked")
    public <O> JpaRepository<O, Object> getRepository(
            final Class<O> type) {

        Class<? super O> cls = type;

        if (REPOSITORY_MAP.containsKey(cls)) {
            return (JpaRepository<O, Object>) REPOSITORY_MAP.get(cls);
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
        
        REPOSITORY_MAP.put(cls, (JpaRepository<?, Object>) repository.get());
        
        return (JpaRepository<O, Object>) repository.get();

    }

    @Override
    public Class<?> getIdType(Class<?> type) {
        
        Class<?> cls = type;

        if (ENTITYINFO_MAP.containsKey(cls)) {
            return ENTITYINFO_MAP
                    .get(cls)
                    .getIdType();
        }
        
        var repositories = new Repositories(applicationContext);
        
        EntityInformation<?, Object> entityInfo;
        do {
            entityInfo = repositories.getEntityInformationFor(cls);
            cls = entityInfo != null ? cls : cls.getSuperclass();
        } while ((entityInfo == null) && (cls != Object.class));
        
        if (entityInfo == null) {
            throw new IllegalStateException(
                    String.format("Type '%s' is not an entity!", type.getName()));
        }
        
        ENTITYINFO_MAP.put(cls, entityInfo);
        
        return entityInfo.getIdType();
        
    }

    private Class<?> getSuperclass(
            final Class<?> cls) {

        return cls.getSuperclass();

    }

    public String getIdName(
            final Class<?> type) {

        // TODO: also check annotated getter methods
        return Stream
                .iterate(type, Objects::nonNull, this::getSuperclass)
                .flatMap(c -> Stream.of(c.getDeclaredFields()))
                .filter(this::isIdAnnotationPresent)
                .findFirst()
                .map(Field::getName)
                .orElse(null);

    }

    private boolean isIdAnnotationPresent(Field field){
        return field.isAnnotationPresent(Id.class) ||
                field.isAnnotationPresent(org.springframework.data.annotation.Id.class);
    }

    @SuppressWarnings("unchecked")
    public <I> I getId(
            final Object domainEntity) {
        
        final var id = containerEntityManagerFactoryBean
                .getNativeEntityManagerFactory()
                .getPersistenceUnitUtil()
                .getIdentifier(domainEntity);
        if (id == null) {
            return null;
        }
        return (I) id;
        
    }

    @Override
    public <O> boolean isPersistedEntity(
            final Class<O> entityClass,
            final O entity) {
        
        final var em = jpaContext
                .getEntityManagerByManagedType(entityClass);
        if (em.contains(entity)) {
            return true;
        }
        final var id = getId(entity);
        if (id == null) {
            return false;
        }
        return em.find(entityClass, id) != null;

    }
    
    @SuppressWarnings("unchecked")
    @Override
    public <O> O unproxy(
            final O entity) {
        
        return (O) Hibernate.unproxy(entity);
        
    }
    
}
