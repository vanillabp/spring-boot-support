package io.vanillabp.springboot.adapter;

import org.springframework.beans.factory.InjectionPoint;
import org.springframework.data.repository.CrudRepository;

import java.lang.reflect.ParameterizedType;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

public abstract class AdapterConfigurationBase<P extends ProcessServiceImplementation<?>> {

    private Map<Class<?>, P> connectableServices = new HashMap<>();

    protected Collection<P> getConnectableServices() {

        return connectableServices.values();

    }

    @SuppressWarnings("unchecked")
    protected <DE> P registerProcessService(
            final SpringDataUtil springDataUtil,
            final InjectionPoint injectionPoint,
            final BiFunction<CrudRepository<DE, String>, Class<DE>, P> processServiceBeanSupplier) throws Exception {
        
        final ParameterizedType processServiceGenericType; 
        if (injectionPoint.getMethodParameter() != null) {
            processServiceGenericType = 
                    (ParameterizedType) injectionPoint
                    .getMethodParameter()
                    .getGenericParameterType();
        } else if (injectionPoint.getField() != null) {
            processServiceGenericType =
                    (ParameterizedType) injectionPoint
                    .getField()
                    .getGenericType();
        } else {
            throw new RuntimeException("Unsupported injection of ProcessService, only field-, constructor- and method-parameter-injection allowed!");
        }
        final Class<DE> workflowAggregateClass = (Class<DE>)
                processServiceGenericType.getActualTypeArguments()[0];
        
        final var existingService = connectableServices.get(workflowAggregateClass);
        if (existingService != null) {
            return (P) existingService;
        }

        final var workflowAggregateRepository = springDataUtil
                .getRepository(workflowAggregateClass);

        final var result = processServiceBeanSupplier.apply(
                workflowAggregateRepository,
                workflowAggregateClass);

        connectableServices.put(workflowAggregateClass, result);

        return result;
        
    }

}
