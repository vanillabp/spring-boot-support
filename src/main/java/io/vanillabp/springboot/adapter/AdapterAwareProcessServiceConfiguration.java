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
import org.springframework.util.CollectionUtils;

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
            
            throw new RuntimeException(
                    "No VanillaBP adapter was found in classpath!");
            
        }
        
        if ((adapterConfigurations.size() > 1)
                && ((properties == null)
                        || CollectionUtils.isEmpty(properties.getDefaultAdapter()))) {
            
            throw new RuntimeException(
                    "More than one VanillaBP adapter is found in classpath: '"
                    + listOfAdapters
                    + "'! You have to define a default using the property 'vanillabp.default-adapter'.");
            
        }
        
        if ((properties != null)
                && !CollectionUtils.isEmpty(properties.getDefaultAdapter())
                && properties
                        .getDefaultAdapter()
                        .stream()
                        .filter(adapter -> adapterConfigurations
                                .stream()
                                .noneMatch(c -> c.getAdapterId().equals(adapter)))
                        .findFirst()
                        .isPresent()) {
            
            final var missingAdapters = properties
                    .getDefaultAdapter()
                    .stream()
                    .filter(adapter -> adapterConfigurations
                            .stream()
                            .noneMatch(c -> c.getAdapterId().equals(adapter)))
                    .collect(Collectors.joining("', '"));
            
            throw new RuntimeException(
                    "Property 'vanillabp.default-adapter' is set to '"
                    + properties.getDefaultAdapter().stream().collect(Collectors.joining(", "))
                    + "' but this/these adapters are not in classpath: '"
                    + missingAdapters
                    + "'! Available adapters are: '"
                    + listOfAdapters
                    + "'.");
            
        }

        if (properties == null) {
            this.properties = new VanillaBpProperties();
        }
        
        if (properties.getDefaultAdapter() == null) {
            properties.setDefaultAdapter(List.of(adapterConfigurations.get(0).getAdapterId()));
        }
        
        properties
                .getWorkflows()
                .stream()
                .collect(Collectors.groupingBy(workflow ->
                        workflow.getWorkflowModuleId()
                        + "#"
                        + workflow.getBpmnProcessId(),
                        Collectors.counting()))
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue() > 1)
                .peek(entry -> {
                    final var workflowModuleIdAndBpmnProcessId = entry.getKey().split("#");
                    if (workflowModuleIdAndBpmnProcessId[0].equals("null")) {
                        logger.error(
                                "BPMN process id '{}' was found more than one time "
                                + "in property 'vanillabp.adapters'!",
                                workflowModuleIdAndBpmnProcessId[1]);
                    } else {
                        logger.error(
                                "BPMN process id '{}' of workflow module '{}' was "
                                + "found more than one time in property 'vanillabp.adapters'!",
                                workflowModuleIdAndBpmnProcessId[1],
                                workflowModuleIdAndBpmnProcessId[0]);
                    }
                })
                .findFirst()
                .ifPresent(entry -> {
                    throw new RuntimeException(
                            "At least one BPMN process id was configured more "
                            + "than once in property 'vanillabp.workflows'! "
                            + "Check previous error logs for details.");
                });
        
        properties
                .getWorkflows()
                .stream()
                .filter(workflow -> {
                        final var hasUnknownAdapterConfigured = workflow
                                .getAdapter()
                                .stream()
                                .filter(adapter -> adapterConfigurations
                                        .stream()
                                        .noneMatch(c -> c.getAdapterId().equals(adapter)))
                                .findFirst()
                                .isPresent();
                        if (!hasUnknownAdapterConfigured) {
                            return false;
                        }
                        
                        final var missingAdapters = workflow
                                .getAdapter()
                                .stream()
                                .filter(adapter -> adapterConfigurations
                                        .stream()
                                        .noneMatch(c -> c.getAdapterId().equals(adapter)))
                                .collect(Collectors.joining("', '"));
                        
                        if (workflow.getWorkflowModuleId() == null) {
                            logger.error(
                                    "Property 'vanillabp.workflows[bpmn-process-id={}].adapters' is set to '{}' "
                                    + "but this/these adapters are not in classpath: '{}'! "
                                    + "Available adapters are: '{}'.",
                                    workflow.getBpmnProcessId(),
                                    workflow.getAdapter().stream().collect(Collectors.joining(", ")),
                                    missingAdapters,
                                    listOfAdapters);
                        } else {
                            logger.error(
                                    "Property 'vanillabp.workflows[bpmn-process-id={}, workflow-module-id={}].adapters' is set to '{}' "
                                    + "but this/these adapters are not in classpath: '{}'! "
                                    + "Available adapters are: '{}'.",
                                    workflow.getBpmnProcessId(),
                                    workflow.getWorkflowModuleId(),
                                    workflow.getAdapter().stream().collect(Collectors.joining(", ")),
                                    missingAdapters,
                                    listOfAdapters);
                        }
                        
                        return true;
                })
                .findFirst()
                .ifPresent(entry -> {
                    throw new RuntimeException(
                            "At least once the property 'vanillabp.workflows.*.adapter' "
                            + "was set to an adapter not available in classpath! "
                            + "Check previous error logs for details.");
                });
        
    }

}
