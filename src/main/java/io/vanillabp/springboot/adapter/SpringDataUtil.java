package io.vanillabp.springboot.adapter;

import org.springframework.data.annotation.Id;
import org.springframework.data.repository.CrudRepository;

/**
 * Utility to handle Spring data repositories. A string identifier is expected for workflow entities.
 */
public interface SpringDataUtil {

    /**
     * Determine the repository for the given object.
     * 
     * @param <O> The entity's expected type.
     * @param entity The entity
     * @return The repository
     */
    <O> CrudRepository<? super O, Object> getRepository(O entity);
    
    /**
     * Determine the repository for the given type.
     * 
     * @param <O> The entity's expected type.
     * @param type The given entity's type.
     * @return The repository
     */
    <O> CrudRepository<O, Object> getRepository(Class<O> type);

    /**
     * Determine the entity's object identifier.
     * 
     * @param entity The entity
     * @return The id
     * @see Id
     */
    <I> I getId(Object entity);

    /**
     * Determine the entity's object identifier's parameter name.
     *
     * @param type The given entity's type.
     * @return The entity's object identifier's parameter name.
     */
    String getIdName(Class<?> type);
    
    /**
     * Determine the entity's object identifier's type.
     * 
     * @param type The given entity's type.
     * @return The entity's object identifier's type.
     */
    Class<?> getIdType(Class<?> type);
    
    /**
     * Unproxy the entity. This forces to load data if the proxy is not yet initialized. 
     * 
     * @param <O> The entity's type
     * @param entity The entity
     * @return The initialized entity
     */
    <O> O unproxy(O entity);
    
    /**
     * Determines whether the given entity was loaded/persisted from/to DB before
     * or it is a POJO (e.g. right before persisting).
     * 
     * @param <O> the entity's type
     * @param entityClass The entity's class
     * @param entity The entity
     * @return Entity was loaded/persisted from/to DB before
     */
    <O> boolean isPersistedEntity(Class<O> entityClass, O entity);
    
}
