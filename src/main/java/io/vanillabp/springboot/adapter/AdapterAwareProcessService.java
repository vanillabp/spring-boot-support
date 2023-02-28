package io.vanillabp.springboot.adapter;

import io.vanillabp.spi.process.ProcessService;
import io.vanillabp.springboot.adapter.VanillaBpProperties.WorkflowAndModuleAdapters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A process service which is aware of multiple adapter-specific process services.
 * <p>
 * On starting a workflow the primary adapter is used. For operations based
 * on existing workflows each adapter is tried to complete the respective action.
 * As the particular workflow was started before using one of the configured
 * adapters, the action should complete successfully. Message correlation
 * is done for each adapter.
 * <p>
 * @see VanillaBpProperties#getDefaultAdapter()
 * @see VanillaBpProperties#getWorkflows()
 * @see WorkflowAndModuleAdapters#getAdapter()
 */
public class AdapterAwareProcessService<DE> implements ProcessService<DE> {
    
    private static final Logger logger = LoggerFactory.getLogger(AdapterAwareProcessService.class);

    private final VanillaBpProperties properties;

    private final Map<String, ProcessServiceImplementation<DE>> processServicesByAdapter;

    private String workflowModuleId;

    private String primaryBpmnProcessId;
    
    private Set<String> bpmnProcessIds = new HashSet<>();

    private Collection<String> messageBasedStartEventsMessageNames;
    
    @SuppressWarnings("unused")
    private Collection<String> signalBasedStartEventsSignalNames;
    
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
            final boolean isPrimary,
            final Collection<String> messageBasedStartEventsMessageNames,
            final Collection<String> signalBasedStartEventsSignalNames) {
        
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
            this.messageBasedStartEventsMessageNames = messageBasedStartEventsMessageNames;
            this.signalBasedStartEventsSignalNames = signalBasedStartEventsSignalNames;
        }
        this.bpmnProcessIds.add(bpmnProcessId);
        
    }

    private List<String> determineAdapterIds() {
        
        return properties
                .getWorkflows()
                .stream()
                .filter(workflow -> workflow.matchesAny(workflowModuleId, bpmnProcessIds))
                .findFirst()
                .map(WorkflowAndModuleAdapters::getAdapter)
                .filter(adapter -> !adapter.isEmpty())
                .orElse(properties.getDefaultAdapter());

    }
    
    private String determinePrimaryAdapterId() {
        
        return determineAdapterIds().get(0);
        
    }
    
    @Override
    public DE startWorkflow(
            final DE workflowAggregate) throws Exception {
        
        return processServicesByAdapter
                .get(determinePrimaryAdapterId())
                .startWorkflow(workflowAggregate);

    }

    @Override
    public DE correlateMessage(
            final DE workflowAggregate,
            final String messageName) {
        
        if (messageBasedStartEventsMessageNames.contains(messageName)) {

            return processServicesByAdapter
                    .get(determinePrimaryAdapterId())
                    .correlateMessage(workflowAggregate, messageName);
            
        } else {
        
            return determineAdapterIds()
                    .stream()
                    .map(processServicesByAdapter::get)
                    .map(adapter -> adapter.correlateMessage(workflowAggregate, messageName))
                    .collect(Collectors.toList())
                    .stream()
                    .findFirst()
                    .get();
            
        }
        
    }

    @Override
    public DE correlateMessage(
            final DE workflowAggregate,
            final String messageName,
            final String correlationId) {
        
        if (messageBasedStartEventsMessageNames.contains(messageName)) {

            return processServicesByAdapter
                    .get(determinePrimaryAdapterId())
                    .correlateMessage(workflowAggregate, messageName, correlationId);
            
        } else {
        
            return determineAdapterIds()
                    .stream()
                    .map(processServicesByAdapter::get)
                    .map(adapter -> adapter.correlateMessage(workflowAggregate, messageName, correlationId))
                    .findFirst()
                    .get();
            
        }
        
    }

    @Override
    public DE correlateMessage(
            final DE workflowAggregate,
            final Object message) {
        
        if (messageBasedStartEventsMessageNames.contains(message.getClass().getSimpleName())) {

            return processServicesByAdapter
                    .get(determinePrimaryAdapterId())
                    .correlateMessage(workflowAggregate, message);
            
        } else {
        
            return determineAdapterIds()
                    .stream()
                    .map(processServicesByAdapter::get)
                    .map(adapter -> adapter.correlateMessage(workflowAggregate, message))
                    .findFirst()
                    .get();
            
        }
        
    }

    @Override
    public DE correlateMessage(
            final DE workflowAggregate,
            final Object message,
            final String correlationId) {
        
        if (messageBasedStartEventsMessageNames.contains(message.getClass().getSimpleName())) {

            return processServicesByAdapter
                    .get(determinePrimaryAdapterId())
                    .correlateMessage(workflowAggregate, message, correlationId);
            
        } else {
        
            return determineAdapterIds()
                    .stream()
                    .map(processServicesByAdapter::get)
                    .map(adapter -> adapter.correlateMessage(workflowAggregate, message, correlationId))
                    .findFirst()
                    .get();
            
        }
        
    }

    @Override
    public DE completeUserTask(
            final DE workflowAggregate,
            final String taskId) {
        
        final var exceptions = new LinkedList<Exception>();
        return determineAdapterIds()
                .stream()
                .map(processServicesByAdapter::get)
                .map(adapter -> {
                    try {
                        return adapter.completeUserTask(workflowAggregate, taskId);
                    } catch (Exception e) {
                        exceptions.add(e);
                        return null;
                    }
                })
                .filter(result -> result != null)
                .findFirst()
                .orElseThrow(() -> {
                    exceptions.forEach(e -> logger.error("Could not complete user-task using adapter '{}'!", e));
                    return new RuntimeException("Unknown taskId '" + taskId + "'!");
                });
        
    }

    @Override
    public DE cancelUserTask(
            final DE workflowAggregate,
            final String taskId,
            final String bpmnErrorCode) {
        
        final var exceptions = new LinkedList<Exception>();
        return determineAdapterIds()
                .stream()
                .map(processServicesByAdapter::get)
                .map(adapter -> {
                    try {
                        return adapter.cancelUserTask(workflowAggregate, taskId, bpmnErrorCode);
                    } catch (Exception e) {
                        exceptions.add(e);
                        return null;
                    }
                })
                .filter(result -> result != null)
                .findFirst()
                .orElseThrow(() -> {
                    exceptions.forEach(e -> logger.error("Could not cancel user-task using adapter '{}'!", e));
                    return new RuntimeException("Unknown taskId '" + taskId + "'!");
                });
        
    }

    @Override
    public DE completeTask(
            final DE workflowAggregate,
            final String taskId) {
        
        final var exceptions = new LinkedList<Exception>();
        return determineAdapterIds()
                .stream()
                .map(processServicesByAdapter::get)
                .map(adapter -> {
                    try {
                        return adapter.completeTask(workflowAggregate, taskId);
                    } catch (Exception e) {
                        exceptions.add(e);
                        return null;
                    }
                })
                .filter(result -> result != null)
                .findFirst()
                .orElseThrow(() -> {
                    exceptions.forEach(e -> logger.error("Could not complete task using adapter '{}'!", e));
                    return new RuntimeException("Unknown taskId '" + taskId + "'!");
                });
        
    }

    @Override
    public DE cancelTask(
            final DE workflowAggregate,
            final String taskId,
            final String bpmnErrorCode) {
        
        final var exceptions = new LinkedList<Exception>();
        return determineAdapterIds()
                .stream()
                .map(processServicesByAdapter::get)
                .map(adapter -> {
                    try {
                        return adapter.cancelTask(workflowAggregate, taskId, bpmnErrorCode);
                    } catch (Exception e) {
                        exceptions.add(e);
                        return null;
                    }
                })
                .filter(result -> result != null)
                .findFirst()
                .orElseThrow(() -> {
                    exceptions.forEach(e -> logger.error("Could not cancel task using adapter '{}'!", e));
                    return new RuntimeException("Unknown taskId '" + taskId + "'!");
                });
        
    }
    
}