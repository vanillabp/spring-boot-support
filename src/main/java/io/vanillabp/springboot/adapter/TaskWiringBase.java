package io.vanillabp.springboot.adapter;

import io.vanillabp.spi.service.TaskEvent;
import io.vanillabp.spi.service.TaskId;
import io.vanillabp.spi.service.WorkflowTask;
import io.vanillabp.springboot.adapter.wiring.AbstractTaskWiring;
import io.vanillabp.springboot.parameters.MethodParameter;
import io.vanillabp.springboot.parameters.MethodParameterFactory;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Collection;
import java.util.List;
import org.springframework.context.ApplicationContext;

public abstract class TaskWiringBase<T extends Connectable, PS extends ProcessServiceImplementation<?>, M extends MethodParameterFactory>
        extends AbstractTaskWiring<T, WorkflowTask, M> {

    public TaskWiringBase(
            final ApplicationContext applicationContext,
            final SpringBeanUtil springBeanUtil,
            final M methodParameterFactory) {
        
        super(applicationContext, springBeanUtil, methodParameterFactory);
        
    }

    @SuppressWarnings("unchecked")
    public TaskWiringBase(
            final ApplicationContext applicationContext,
            final SpringBeanUtil springBeanUtil) {

        this(applicationContext, springBeanUtil, (M) new MethodParameterFactory());
        
    }

    protected abstract <DE> PS connectToBpms(
            String workflowModuleId,
            Class<DE> workflowAggregateClass,
            String bpmnProcessId,
            boolean isPrimary,
            Collection<String> messageBasedStartEventsMessageNames,
            Collection<String> signalBasedStartEventsSignalNames);

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
    
    protected abstract void connectToBpms(
            String workflowModuleId,
            PS processService,
            Object bean,
            T connectable,
            Method method,
            List<MethodParameter> parameters);

    protected boolean methodMatchesElementId(
            final T connectable,
            final Method method,
            final WorkflowTask annotation) {

        if (!annotation.taskDefinition().equals(WorkflowTask.USE_METHOD_NAME)) {
            return false;
        }

        if (annotation.id().equals(WorkflowTask.USE_METHOD_NAME)
                && method.getName().equals(connectable.getElementId())) {
            return true;
        }

        if (annotation.id().equals(connectable.getElementId())) {
            return true;
        }

        return false;

    }

    protected boolean methodMatchesTaskDefinition(
            final T connectable,
            final Method method,
            final WorkflowTask annotation) {

        if (!annotation.id().equals(WorkflowTask.USE_METHOD_NAME)) {
            return false;
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
        
        if (!void.class.equals(method.getReturnType())) {
            throw new RuntimeException(
                    "Expected return-type 'void' for '"
                    + method
                    + "' but got: "
                    + method.getReturnType());
        }
        
        return super.validateParameters(
                method,
                (m, parameter, i) -> validateWorkflowAggregateParameter(
                        processService.getWorkflowAggregateClass(),
                        m,
                        parameter,
                        i),
                super::validateTaskParam,
                this::validateTaskId,
                this::validateTaskEvent,
                super::validateMultiInstanceTotal,
                super::validateMultiInstanceIndex,
                super::validateMultiInstanceElement);
        
    }
    
    protected MethodParameter validateTaskId(
            final Method method,
            final Parameter parameter,
            final int index) {

        final var userTaskIdAnnotation = parameter.getAnnotation(TaskId.class);
        if (userTaskIdAnnotation == null) {
            return null;
        }

        return methodParameterFactory.getTaskIdParameter(
                index,
                parameter.getName());
        
    }
    
    protected MethodParameter validateTaskEvent(
            final Method method,
            final Parameter parameter,
            final int index) {

        final var userTaskEventAnnotation = parameter.getAnnotation(TaskEvent.class);
        if (userTaskEventAnnotation == null) {
            return null;
        }

        return methodParameterFactory
                .getUserTaskEventParameter(
                        index,
                        parameter.getName(),
                        userTaskEventAnnotation.value());

    }
    
}
