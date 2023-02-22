package io.vanillabp.springboot.adapter;

import io.vanillabp.spi.process.ProcessService;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class AdapterAwareProcessService<DE> implements ProcessService<DE> {

    private final VanillaBpProperties properties;

    private final Map<String, ProcessServiceImplementation<DE>> processServicesByAdapter;

    private String workflowModuleId;

    private String primaryBpmnProcessId;
    
    private Set<String> bpmnProcessIds = new HashSet<>();

    public AdapterAwareProcessService(
            final VanillaBpProperties properties,
            final Map<String, ProcessServiceImplementation<DE>> processServicesByAdapter) {
        
        this.properties = properties;
        this.processServicesByAdapter = processServicesByAdapter;
        
        processServicesByAdapter.forEach((adapterId, adapter) -> adapter.setParent(this));
        
    }
    
    public String getPrimaryBpmnProcessId() {
        
        return primaryBpmnProcessId;
        
    }
    
    public Collection<String> getBpmnProcessIds() {
        
        return bpmnProcessIds;
        
    }
    
    public String getWorkflowModuleId() {
        
        return workflowModuleId;
        
    }
    
    public void wire(
            final String adapterId,
            final String workflowModuleId,
            final String bpmnProcessId,
            final boolean isPrimary) {
        
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

        if (bpmnProcessIds.contains(bpmnProcessId)
                && !processServicesByAdapter.containsKey(adapterId)) {
            
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
                    + "' is not possible, because it was already wired by these adapters: '"
                    + listOfAdapters
                    + "'!");
            
        }

        if (this.workflowModuleId == null) {
            this.workflowModuleId = workflowModuleId;
        }
        if (isPrimary) {
            this.primaryBpmnProcessId = bpmnProcessId;
        }
        this.bpmnProcessIds.add(bpmnProcessId);
        
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
                        .matchesAny(workflowModuleId, bpmnProcessIds))
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
