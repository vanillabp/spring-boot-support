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
    <O> CrudRepository<? super O, String> getRepository(O entity);
    
    /**
     * Determine the repository for the given type.
     * 
     * @param <O> The entity's expected type.
     * @param object The given entity's type.
     * @return The repository
     */
    <O> CrudRepository<O, String> getRepository(Class<O> type);

    /**
     * Determine the entity's object identifier.
     * 
     * @param entity The entity
     * @return The id
     * @see Id
     */
    String getId(Object entity);
    
    /**
     * Unproxy the entity. This forces to load data if the proxy is not yet initialized. 
     * 
     * @param <O> The entity's type
     * @param entity The entity
     * @return The initialized entity
     */
    <O> O unproxy(O entity);
    
}
