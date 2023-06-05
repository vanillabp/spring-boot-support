package io.vanillabp.springboot.adapter;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationContext;
import org.springframework.util.StringUtils;

import io.vanillabp.spi.service.BpmnProcess;
import io.vanillabp.spi.service.TaskEvent;
import io.vanillabp.spi.service.TaskId;
import io.vanillabp.spi.service.WorkflowService;
import io.vanillabp.spi.service.WorkflowTask;
import io.vanillabp.springboot.adapter.wiring.AbstractTaskWiring;
import io.vanillabp.springboot.parameters.MethodParameter;
import io.vanillabp.springboot.parameters.MethodParameterFactory;

public abstract class TaskWiringBase<T extends Connectable, PS extends ProcessServiceImplementation<?>>
        extends AbstractTaskWiring<T, WorkflowTask> {

    public TaskWiringBase(
            final ApplicationContext applicationContext,
            final MethodParameterFactory methodParameterFactory) {
        
        super(applicationContext, methodParameterFactory);
        
    }

    public TaskWiringBase(
            final ApplicationContext applicationContext) {
        
        this(applicationContext, new MethodParameterFactory());
        
    }

    protected abstract <DE> PS connectToBpms(
            String workflowModuleId,
            Class<DE> workflowAggregateClass,
            String bpmnProcessId,
            boolean isPrimary,
            Collection<String> messageBasedStartEventsMessageNames,
            Collection<String> signalBasedStartEventsSignalNames);

    protected Entry<Class<?>, Class<?>> determineWorkflowAggregateClass(
            final Object bean) {

        final var serviceClass = determineBeanClass(bean);

        final var aggregateClassNames = new LinkedList<String>();
        
        final var workflowAggregateClass = Arrays
                .stream(serviceClass.getAnnotationsByType(WorkflowService.class))
                .collect(Collectors.groupingBy(annotation -> annotation.workflowAggregateClass()))
                .keySet()
                .stream()
                .peek(aggregateClass -> aggregateClassNames.add(aggregateClass.getName()))
                .findFirst()
                .get();
        
        return Map.entry(
                serviceClass,
                workflowAggregateClass);

    }

    private boolean isExtendingWorkflowServicePort(
            final Entry<Class<?>, Class<?>> classes) {

        return classes != null;

    }

    public PS wireService(
            final String workflowModuleId,
            final String bpmnProcessId,
            final Collection<String> messageBasedStartEventsMessageNames,
            final Collection<String> signalBasedStartEventsSignalNames) {

        final var workflowAggregateAndServiceClass =
                determineAndValidateWorkflowAggregateAndServiceClass(bpmnProcessId);
        final var workflowAggregateClass = workflowAggregateAndServiceClass.getKey();
        final var workflowServiceClass = workflowAggregateAndServiceClass.getValue();
        
        final var isPrimaryProcessWiring = isPrimaryProcessWiring(
                workflowModuleId,
                bpmnProcessId,
                workflowServiceClass);
        
        return connectToBpms(
                workflowModuleId,
                workflowAggregateClass,
                bpmnProcessId,
                isPrimaryProcessWiring,
                messageBasedStartEventsMessageNames,
                signalBasedStartEventsSignalNames);
        
    }

    private Map.Entry<Class<?>, Class<?>> determineAndValidateWorkflowAggregateAndServiceClass(
            final String bpmnProcessId) {

        final var tested = new StringBuilder();
        
        final var matchingServices = applicationContext
                .getBeansWithAnnotation(WorkflowService.class)
                .entrySet()
                .stream()
                .peek(bean -> {
                    if (tested.length() > 0) {
                        tested.append(", ");
                    }
                    tested.append(bean.getKey());
                })
                .filter(bean -> isAboutConnectableProcess(bpmnProcessId, bean.getValue()))
                .map(bean -> determineWorkflowAggregateClass(bean.getValue()))
                .filter(this::isExtendingWorkflowServicePort)
                .collect(Collectors.groupingBy(
                        Entry::getValue,
                        Collectors.mapping(Entry::getKey, Collectors.toList())));

        if (matchingServices.size() == 0) {
            throw new RuntimeException(
                    "No bean annotated with @WorkflowService(bpmnProcessId=\""
                    + bpmnProcessId
                    + "\"). Tested for: "
                    + tested);
        }
        
        if (matchingServices.size() != 1) {
            
            final var found = new StringBuilder();
            matchingServices
                    .entrySet()
                    .stream()
                    .peek(entry -> {
                        if (found.length() > 0) {
                            found.append("; ");
                        }
                        found.append(entry.getKey().getName());
                        found.append(" by ");
                    })
                    .flatMap(entry -> entry.getValue().stream())
                    .forEach(matchingService -> {
                        if (found.length() > 0) {
                            found.append(", ");
                        }
                        found.append(matchingService.getName());
                    });
            throw new RuntimeException(
                    "Multiple beans annotated with @WorkflowService(bpmnProcessId=\""
                    + bpmnProcessId
                    + "\") found having different generic parameters, but should all be the same: "
                    + found);
            
        }
        
        final var matchingService = matchingServices
                .entrySet()
                .iterator()
                .next();

        return Map.entry(
                matchingService.getKey(),
                matchingService.getValue().get(0));
        
    }

    private boolean isPrimaryProcessWiring(
            final String workflowModuleId,
            final String bpmnProcessId,
            final Class<?> workflowServiceClass) {

        final var primaryBpmnProcessIds = Arrays
                .stream(workflowServiceClass.getAnnotationsByType(WorkflowService.class))
                .map(WorkflowService::bpmnProcess)
                .map(bpmnProcess -> bpmnProcess.bpmnProcessId().equals(BpmnProcess.USE_CLASS_NAME)
                        ? workflowServiceClass.getSimpleName()
                        : bpmnProcess.bpmnProcessId())
                .collect(Collectors.toList());
        if (primaryBpmnProcessIds.size() > 1) {
            final var bpmnProcessIds = primaryBpmnProcessIds
                    .stream()
                    .collect(Collectors.joining("', '"));
            throw new RuntimeException("In class '"
                    + workflowServiceClass.getName()
                    + (StringUtils.hasText(workflowModuleId)
                        ? ""
                        : "' of workflow module '")
                    + "' there is more than one @BpmnProcess annotation having "
                    + "set attribute 'primary' as true: '"
                    + bpmnProcessIds
                    + "'. Please have a look into "
                    + "the attribute's JavaDoc to learn about its meaning.");
        }
        
        return primaryBpmnProcessIds.get(0).equals(bpmnProcessId);
        
    }
    
    protected abstract void connectToBpms(
            PS processService,
            Object bean,
            T connectable,
            Method method,
            List<MethodParameter> parameters);
    
    protected boolean methodMatchesTaskDefinition(
            final T connectable,
            final Method method,
            final WorkflowTask annotation) {
        
        if (super.methodMatchesTaskDefinition(connectable, method, annotation)) {
            return true;
        }

        if (annotation.taskDefinition().equals(WorkflowTask.USE_METHOD_NAME)
                && method.getName().equals(connectable.getTaskDefinition())) {
            return true;
        }

        if (annotation.taskDefinition().equals(connectable.getTaskDefinition())) {
            return true;
        }

        return false;
        
    }

    @SuppressWarnings("unchecked")
    protected List<MethodParameter> validateParameters(
            final PS processService,
            final Method method) {
        
        return super.validateParameters(
                method,
                (m, parameter) -> validateWorkflowAggregateParameter(
                        processService.getWorkflowAggregateClass(),
                        m,
                        parameter),
                super::validateTaskParam,
                this::validateTaskId,
                this::validateTaskEvent,
                super::validateMultiInstanceTotal,
                super::validateMultiInstanceIndex,
                super::validateMultiInstanceElement);
        
    }
    
    protected MethodParameter validateTaskId(
            final Method method,
            final Parameter parameter) {

        final var userTaskIdAnnotation = parameter.getAnnotation(TaskId.class);
        if (userTaskIdAnnotation == null) {
            return null;
        }

        return methodParameterFactory.getTaskIdParameter(
                parameter.getName());
        
    }
    
    protected MethodParameter validateTaskEvent(
            final Method method,
            final Parameter parameter) {

        final var userTaskEventAnnotation = parameter.getAnnotation(TaskEvent.class);
        if (userTaskEventAnnotation == null) {
            return null;
        }

        return methodParameterFactory
                .getUserTaskEventParameter(
                        parameter.getName(),
                        userTaskEventAnnotation.value());

    }
    
}
