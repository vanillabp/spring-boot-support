package io.vanillabp.springboot.utils;

import io.vanillabp.springboot.adapter.SpringDataUtil;
import org.hibernate.Hibernate;
import org.springframework.context.ApplicationContext;
import org.springframework.data.jpa.repository.JpaContext;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.support.Repositories;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class JpaSpringDataUtil implements SpringDataUtil {

    private static final Map<Class<?>, JpaRepository<?, String>> REPOSITORY_MAP = new HashMap<>();
    
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
    public <O> JpaRepository<? super O, String> getRepository(
            final O object) {

        //noinspection unchecked
        return getRepository((Class<O>) object.getClass());

    }

    @SuppressWarnings("unchecked")
    public <O> JpaRepository<O, String> getRepository(
            final Class<O> type) {

        Class<? super O> cls = type;

        if (REPOSITORY_MAP.containsKey(cls))
            return (JpaRepository<O, String>) REPOSITORY_MAP.get(cls);

        var repositories = new Repositories(applicationContext);

        Optional<Object> repository;

        do {
            repository = repositories.getRepositoryFor(cls);
            cls = repository.isPresent() ? cls : cls.getSuperclass();
        } while (repository.isEmpty() && cls != Object.class);

        if (repository.isPresent()) {
            REPOSITORY_MAP.put(cls, (JpaRepository<?, String>) repository.get());
            return (JpaRepository<O, String>) REPOSITORY_MAP.get(cls);
        }

        throw new IllegalStateException(
                String.format("No Spring Data repository defined for %s", type.getName()));

    }

    public String getId(
            final Object domainEntity) {
        
        final var id = containerEntityManagerFactoryBean
                .getNativeEntityManagerFactory()
                .getPersistenceUnitUtil()
                .getIdentifier(domainEntity);
        if (id == null) {
            return null;
        }
        return id.toString();
        
    }
    
    @Override
    public <O> boolean isPersistedEntity(
            final Class<O> entityClass,
            final O entity) {
        
        final var em = jpaContext
                .getEntityManagerByManagedType(entityClass);
        return em.contains(entity);

    }
    
    @SuppressWarnings("unchecked")
    @Override
    public <O> O unproxy(
            final O entity) {
        
        return (O) Hibernate.unproxy(entity);
        
    }
    
}
