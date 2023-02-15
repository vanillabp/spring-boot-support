package io.vanillabp.springboot.adapter;

import io.vanillabp.spi.process.ProcessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InjectionPoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import org.springframework.util.StringUtils;

import java.lang.reflect.ParameterizedType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@EnableConfigurationProperties(VanillaBpProperties.class)
public class AdapterAwareProcessServiceConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(AdapterAwareProcessServiceConfiguration.class);
    
    private Map<Class<?>, AdapterAwareProcessService<?>> connectableServices = new HashMap<>();

    @Autowired(required = false)
    private VanillaBpProperties properties;
    
    @Autowired
    private List<AdapterConfigurationBase<?>> adapterConfigurations;
    
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
        
        validateConfiguration();

        final var processServicesByAdapter = adapterConfigurations
                .stream()
                .map(adapter -> Map.entry(
                        adapter.getAdapterId(),
                        adapter.newProcessServiceImplementation(
                                springDataUtil,
                                workflowAggregateClass,
                                workflowAggregateRepository)))
                .collect(Collectors
                        .toMap(
                                Map.Entry::getKey,
                                Map.Entry::getValue));

        @SuppressWarnings("rawtypes")
        final var result = new AdapterAwareProcessService(
                properties,
                processServicesByAdapter);

        connectableServices.put(workflowAggregateClass, result);

        return result;
            
    }

    private void validateConfiguration() {
        
        final var listOfAdapters = adapterConfigurations
                .stream()
                .map(AdapterConfigurationBase::getAdapterId)
                .collect(Collectors.joining("', '"));
        
        if (adapterConfigurations.isEmpty()) {
            
            throw new RuntimeException("No VanillaBP adapter was found in classpath!");
            
        }
        
        if ((adapterConfigurations.size() > 1)
                && ((properties == null)
                        || !StringUtils.hasText(properties.getDefaultAdapter()))) {
            
            throw new RuntimeException("More than one VanillaBP adapter is found in classpath: '"
                    + listOfAdapters
                    + "'! You have to define a default using the property 'vanillabp.default-adapter'.");
            
        }
        
        if ((properties != null)
                && StringUtils.hasText(properties.getDefaultAdapter())
                && adapterConfigurations
                        .stream()
                        .filter(c -> c.getAdapterId().equals(properties.getDefaultAdapter()))
                        .findFirst()
                        .isEmpty()) {
            
            throw new RuntimeException("Property 'vanillabp.default-adapter' is set to '"
                    + properties.getDefaultAdapter()
                    + "' but no VanillaBP adapter using this identifier was found in classpath!"
                    + " This is available in classpath: '"
                    + listOfAdapters
                    + "'.");
            
        }

        if (properties == null) {
            this.properties = new VanillaBpProperties();
        }
        
        if (properties.getDefaultAdapter() == null) {
            properties.setDefaultAdapter(adapterConfigurations.get(0).getAdapterId());
        }
        
        properties
                .getAdapters()
                .entrySet()
                .stream()
                .flatMap(entry -> entry
                        .getValue()
                        .getAdapterFor()
                        .stream()
                        .map(item -> Map.entry(entry.getKey(), item)))
                .collect(Collectors.groupingBy(entry ->
                        entry.getValue().getWorkflowModuleId()
                        + "#"
                        + entry.getValue().getBpmnProcessId(),
                        Collectors.mapping(entry -> entry.getKey(), Collectors.toSet())))
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue().size() > 1)
                .peek(entry -> {
                    final var workflowModuleIdAndBpmnProcessId = entry.getKey().split("#");
                    if (workflowModuleIdAndBpmnProcessId[0].equals("null")) {
                        logger.error("BPMN process id '{}' was mapped to more than one VanillaBP adapter: '{}'",
                                workflowModuleIdAndBpmnProcessId[1],
                                entry.getValue().stream().collect(Collectors.joining("', '")));
                    } else {
                        logger.error("BPMN process id '{}' of workflow module '{}' was mapped to more than one VanillaBP adapter: '{}'",
                                workflowModuleIdAndBpmnProcessId[1],
                                workflowModuleIdAndBpmnProcessId[0],
                                entry.getValue().stream().collect(Collectors.joining("', '")));
                    }
                })
                .findFirst()
                .ifPresent(entry -> {
                    throw new RuntimeException("At least one BPMN process id was mapped to more "
                            + "than one VanillaBP adapter! Check previous error logs for details.");
                });
        
    }

}
