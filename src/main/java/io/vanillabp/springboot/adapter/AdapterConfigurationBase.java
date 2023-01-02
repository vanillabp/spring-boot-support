package io.vanillabp.springboot.adapter;

import org.springframework.beans.factory.InjectionPoint;
import org.springframework.core.ResolvableType;
import org.springframework.data.repository.CrudRepository;

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
        
        final var resolvableType = ResolvableType.forField(injectionPoint.getField());

        final var workflowAggregateClass = (Class<DE>) resolvableType
                .getGeneric(0)
                .resolve();

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
