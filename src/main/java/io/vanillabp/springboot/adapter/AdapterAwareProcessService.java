package io.vanillabp.springboot.adapter;

import io.vanillabp.spi.process.ProcessService;

import java.util.Map;
import java.util.stream.Collectors;

public class AdapterAwareProcessService<DE> implements ProcessService<DE> {

    private final VanillaBpProperties properties;

    private final Map<String, ProcessServiceImplementation<DE>> processServicesByAdapter;

    private String workflowModuleId;

    private String bpmnProcessId;

    public AdapterAwareProcessService(
            final VanillaBpProperties properties,
            final Map<String, ProcessServiceImplementation<DE>> processServicesByAdapter) {
        
        this.properties = properties;
        this.processServicesByAdapter = processServicesByAdapter;
        
        processServicesByAdapter.forEach((adapterId, adapter) -> adapter.setParent(this));
        
    }
    
    public void wire(
            final String adapterId,
            final String workflowModuleId,
            final String bpmnProcessId) {
        
        if ((this.workflowModuleId != null)
                && (workflowModuleId != null)
                && !this.workflowModuleId.equals(workflowModuleId)) {
            
            final var listOfAdapters = processServicesByAdapter
                    .keySet()
                    .stream()
                    .collect(Collectors.joining("', '"));
            
            throw new RuntimeException("Wiring the workflowModuleId '"
                    + workflowModuleId
                    + "' given by VanillaBP adapter '"
                    + adapterId
                    + "' to workflow-aggregate-class '"
                    + processServicesByAdapter.values().iterator().next().getWorkflowAggregateClass()
                    + "' is not possible, because it was wired to '"
                    + this.workflowModuleId
                    + "' by these adapters before: '"
                    + listOfAdapters
                    + "'!");
            
        }

        if ((this.bpmnProcessId != null)
                && !this.bpmnProcessId.equals(bpmnProcessId)) {
            
            final var listOfAdapters = processServicesByAdapter
                    .keySet()
                    .stream()
                    .collect(Collectors.joining("', '"));
            
            throw new RuntimeException("Wiring the bpmnProcessId '"
                    + bpmnProcessId
                    + (this.workflowModuleId != null
                            ? "' from workflowModuleId '"
                                + workflowModuleId
                            : "")
                    + "' given by VanillaBP adapter '"
                    + adapterId
                    + "' to workflow-aggregate-class '"
                    + processServicesByAdapter.values().iterator().next().getWorkflowAggregateClass()
                    + "' is not possible, because it was wired to '"
                    + this.bpmnProcessId
                    + "' by these adapters before: '"
                    + listOfAdapters
                    + "'!");
            
        }

        this.workflowModuleId = workflowModuleId;
        this.bpmnProcessId = bpmnProcessId;
        
    }

    private String determineAdapterId() {
        
        return properties
                .getAdapters()
                .entrySet()
                .stream()
                .flatMap(entry -> entry
                        .getValue()
                        .getAdapterFor()
                        .stream()
                        .map(item -> Map.entry(entry.getKey(), item)))
                .filter(entry -> entry
                        .getValue()
                        .matches(workflowModuleId, bpmnProcessId))
                .findFirst()
                .map(Map.Entry::getKey)
                .orElse(properties.getDefaultAdapter());
        
    }
    
    private String determineAdapterId(
            final DE workflowAggregate) {
        
        return determineAdapterId();
        
    }
    
    @Override
    public DE startWorkflow(
            final DE workflowAggregate) throws Exception {
        
        return processServicesByAdapter
                .get(determineAdapterId())
                .startWorkflow(workflowAggregate);

    }

    @Override
    public String getBpmnProcessId() {
        
        return bpmnProcessId;

    }

    @Override
    public DE correlateMessage(
            final DE workflowAggregate,
            final String messageName) {
        
        return processServicesByAdapter
                .get(determineAdapterId(workflowAggregate))
                .correlateMessage(workflowAggregate, messageName);
        
    }

    @Override
    public DE correlateMessage(
            final DE workflowAggregate,
            final String messageName,
            final String correlationId) {
        
        return processServicesByAdapter
                .get(determineAdapterId(workflowAggregate))
                .correlateMessage(workflowAggregate, messageName, correlationId);
        
    }

    @Override
    public DE correlateMessage(
            final DE workflowAggregate,
            final Object message) {
        
        return processServicesByAdapter
                .get(determineAdapterId(workflowAggregate))
                .correlateMessage(workflowAggregate, message);
        
    }

    @Override
    public DE correlateMessage(
            final DE workflowAggregate,
            final Object message,
            final String correlationId) {
        
        return processServicesByAdapter
                .get(determineAdapterId(workflowAggregate))
                .correlateMessage(workflowAggregate, message, correlationId);
        
    }

    @Override
    public DE completeUserTask(
            final DE workflowAggregate,
            final String taskId) {
        
        return processServicesByAdapter
                .get(determineAdapterId(workflowAggregate))
                .completeUserTask(workflowAggregate, taskId);
        
    }

    @Override
    public DE cancelUserTask(
            final DE workflowAggregate,
            final String taskId,
            final String bpmnErrorCode) {
        
        return processServicesByAdapter
                .get(determineAdapterId(workflowAggregate))
                .cancelUserTask(workflowAggregate, taskId, bpmnErrorCode);
        
    }

    @Override
    public DE completeTask(
            final DE workflowAggregate,
            final String taskId) {
        
        return processServicesByAdapter
                .get(determineAdapterId(workflowAggregate))
                .completeTask(workflowAggregate, taskId);
        
    }

    @Override
    public DE cancelTask(
            final DE workflowAggregate,
            final String taskId,
            final String bpmnErrorCode) {
        
        return processServicesByAdapter
                .get(determineAdapterId(workflowAggregate))
                .cancelTask(workflowAggregate, taskId, bpmnErrorCode);
        
    }
    
}
