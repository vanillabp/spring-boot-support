package io.vanillabp.springboot.adapter;

import io.vanillabp.spi.process.ProcessService;
import io.vanillabp.springboot.modules.WorkflowModuleProperties;
import jakarta.annotation.PostConstruct;
import java.lang.reflect.ParameterizedType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InjectionPoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;

@EnableConfigurationProperties(VanillaBpProperties.class)
public class AdapterAwareProcessServiceConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(AdapterAwareProcessServiceConfiguration.class);
    
    private Map<Class<?>, AdapterAwareProcessService<?>> connectableServices = new HashMap<>();

    @Autowired(required = false)
    private VanillaBpProperties properties;

    @Value("${spring.application.name}")
    private String applicationName;

    @Autowired(required = false)
    private List<WorkflowModuleProperties> moduleProperties;

    @Autowired(required = false)
    private List<AdapterConfigurationBase<?>> adapterConfigurations;
    
    @Bean
    public Map<Class<?>, AdapterAwareProcessService<?>> vanillaBpConnectableServices() {
        
        return connectableServices;
        
    }

    @PostConstruct
    public void validateConfiguration() {

        if ((adapterConfigurations == null) || adapterConfigurations.isEmpty()) {
            throw new RuntimeException(
                    "No VanillaBP adapter was found in classpath!");
        }

        final var hasExplicitDefinedWorkflowModules = (moduleProperties != null)
                && !moduleProperties.isEmpty();
        final var workflowModuleIds = hasExplicitDefinedWorkflowModules
                ? moduleProperties.stream().map(WorkflowModuleProperties::getWorkflowModuleId).toList()
                : List.of(applicationName);
        final var adapterIds = adapterConfigurations
                .stream()
                .map(AdapterConfigurationBase::getAdapterId)
                .toList();
        if (properties.getDefaultAdapter().isEmpty()
                && (adapterIds.size() == 1)) {
            properties.setDefaultAdapter(adapterIds);
        }

        properties.validateProperties(adapterIds, workflowModuleIds);

    }
    
    @SuppressWarnings("unchecked")
    @Bean
    @Primary
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public <DE> ProcessService<?> adapterAwareProcessService(
            final SpringDataUtil springDataUtil,
            final InjectionPoint injectionPoint) {

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
            return existingService;
        }

        final var workflowAggregateRepository = springDataUtil
                .getRepository(workflowAggregateClass);
        final var workflowAggregateIdClass = springDataUtil
                .getIdType(workflowAggregateClass);

        final var processServicesByAdapter = adapterConfigurations
                .stream()
                .map(adapter -> Map.entry(
                        adapter.getAdapterId(),
                        adapter.newProcessServiceImplementation(
                                springDataUtil,
                                workflowAggregateClass,
                                workflowAggregateIdClass,
                                workflowAggregateRepository)))
                .collect(Collectors
                        .toMap(
                                Map.Entry::getKey,
                                Map.Entry::getValue));

        @SuppressWarnings("rawtypes")
        final var result = new AdapterAwareProcessService(
                properties,
                processServicesByAdapter,
                workflowAggregateIdClass,
                workflowAggregateClass);

        connectableServices.put(workflowAggregateClass, result);

        return result;
            
    }

}
