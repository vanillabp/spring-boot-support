package io.vanillabp.springboot.adapter;

import io.vanillabp.spi.service.BpmnProcess;
import io.vanillabp.spi.service.MultiInstanceElement;
import io.vanillabp.spi.service.MultiInstanceIndex;
import io.vanillabp.spi.service.MultiInstanceTotal;
import io.vanillabp.spi.service.NoResolver;
import io.vanillabp.spi.service.TaskEvent;
import io.vanillabp.spi.service.TaskId;
import io.vanillabp.spi.service.TaskParam;
import io.vanillabp.spi.service.WorkflowService;
import io.vanillabp.spi.service.WorkflowTask;
import io.vanillabp.springboot.parameters.MethodParameter;
import io.vanillabp.springboot.parameters.MethodParameterFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class TaskWiringBase<T extends Connectable, PS extends ProcessServiceImplementation<?>> {

    protected final ApplicationContext applicationContext;
    
    protected final MethodParameterFactory methodParameterFactory;

    public TaskWiringBase(
            final ApplicationContext applicationContext,
            final MethodParameterFactory methodParameterFactory) {
        
        this.applicationContext = applicationContext;
        this.methodParameterFactory = methodParameterFactory;
        
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

    public void wireTask(
            final PS processService,
            final T connectable) {

        final var tested = new StringBuilder();
        final var matching = new StringBuilder();
        final var matchingMethods = new AtomicInteger(0);
        
        applicationContext
                .getBeansWithAnnotation(WorkflowService.class)
                .entrySet()
                .stream()
                .filter(bean -> isAboutConnectableProcess(
                        connectable.getBpmnProcessId(),
                        bean.getValue()))
                .forEach(bean -> {
                    connectConnectableToBean(
                            processService,
                            connectable,
                            tested,
                            matching,
                            matchingMethods,
                            bean.getKey(),
                            bean.getValue());
                });

        if (matchingMethods.get() > 1) {
            throw new RuntimeException(
                    "More than one method annotated with @WorkflowTask is matching task having "
                    + (StringUtils.hasText(connectable.getTaskDefinition())
                            ? "task-definition '" + connectable.getTaskDefinition()
                            : "element-id '" + connectable.getElementId())
                    + "' of process '"
                    + connectable.getBpmnProcessId()
                    + "': "
                    + matching);
        }
        if (matchingMethods.get() == 0) {
            throw new RuntimeException(
                    "No public method annotated with @WorkflowTask is matching task having "
                    + (StringUtils.hasText(connectable.getTaskDefinition())
                            ? "task-definition '" + connectable.getTaskDefinition()
                            : "no task-definition but element-id '" + connectable.getElementId())
                    + "' of process '"
                    + connectable.getBpmnProcessId()
                    + "'. Tested for: "
                    + tested);
        }

    }

    private boolean isAboutConnectableProcess(
            final String bpmnProcessId,
            final Object bean) {
        
        final var beanClass = determineBeanClass(bean);
        final var workflowServiceAnnotations = beanClass.getAnnotationsByType(WorkflowService.class);

        return Arrays
                .stream(workflowServiceAnnotations)
                .flatMap(workflowServiceAnnotation ->
                        Stream.concat(
                                Stream.of(workflowServiceAnnotation.bpmnProcess()),
                                Arrays.stream(workflowServiceAnnotation.secondaryBpmnProcesses())))
                .anyMatch(annotation -> annotation.bpmnProcessId().equals(bpmnProcessId)
                        || (annotation.bpmnProcessId().equals(BpmnProcess.USE_CLASS_NAME)
                                && bpmnProcessId.equals(beanClass.getSimpleName())));

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
            PS processService, Object bean, T connectable, Method method, List<MethodParameter> parameters);

    private void connectConnectableToBean(
            final PS processService,
            final T connectable,
            final StringBuilder tested,
            final StringBuilder matching,
            final AtomicInteger matchingMethods,
            final String beanName,
            final Object bean) {
        
        final Class<?> beanClass = determineBeanClass(bean);
        
        Arrays
                .stream(beanClass.getMethods())
                .flatMap(method -> Arrays
                        .stream(method.getAnnotationsByType(WorkflowTask.class))
                        .map(annotation -> Map.entry(method, annotation)))
                .peek(m -> {
                    if (tested.length() > 0) {
                        tested.append(", ");
                    }
                    tested.append(m.getKey().toString());
                })
                .filter(m -> methodMatchesTaskDefinition(connectable, m.getKey(), m.getValue())
                        || methodMatchesElementId(connectable, m.getKey(), m.getValue()))
                .peek(m -> {
                    if (matching.length() > 0) {
                        matching.append(", ");
                    }
                    matching.append(m.getKey().toString());
                })
                .filter(m -> matchingMethods.getAndIncrement() == 0)
                .map(m -> validateParameters(processService, m.getKey()))
                .forEach(m -> connectToBpms(
                        processService,
                        bean,
                        connectable,
                        m.getKey(),
                        m.getValue()));
        
    }
    
    private boolean methodMatchesTaskDefinition(
            final T connectable,
            final Method method,
            final WorkflowTask annotation) {
        
        if (!StringUtils.hasText(connectable.getTaskDefinition())) {
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
    
    private boolean methodMatchesElementId(
            final T connectable,
            final Method method,
            final WorkflowTask annotation) {
        
        if (method.getName().equals(connectable.getElementId())) {
            return true;
        }

        if (annotation.id().equals(connectable.getElementId())) {
            return true;
        }

        return false;
        
    }

    private Map.Entry<Method, List<MethodParameter>> validateParameters(
            final PS processService,
            final Method method) {
        
        final var parameters = new LinkedList<MethodParameter>();
        
        final var workflowAggregateClass = processService.getWorkflowAggregateClass();

        final var unknown = new StringBuffer();

        if (!void.class.equals(method.getReturnType())) {
            throw new RuntimeException(
                    "Expected return-type 'void' for '"
                    + method
                    + "' but got: "
                    + method.getReturnType());
        }
        
        final var index = new int[] { -1 };
        Arrays
                .stream(method.getParameters())
                .peek(param -> ++index[0])
                .filter(param -> {
                    final var isWorkflowAggregate = workflowAggregateClass.isAssignableFrom(param.getType());
                    if (!isWorkflowAggregate) {
                        return true;
                    }

                    parameters.add(methodParameterFactory
                            .getWorkflowAggregateMethodParameter());
                    return false;
                }).filter(param -> {
                    final var taskParamAnnotation = param.getAnnotation(TaskParam.class);
                    if (taskParamAnnotation == null) {
                        return true;
                    }

                    parameters.add(methodParameterFactory
                            .getTaskParameter(taskParamAnnotation.value()));
                    return false;
                }).filter(param -> {
                    final var userTaskIdAnnotation = param.getAnnotation(TaskId.class);
                    if (userTaskIdAnnotation == null) {
                        return true;
                    }

                    parameters.add(methodParameterFactory.getTaskIdParameter());
                    return false;
                }).filter(param -> {
                    final var userTaskEventAnnotation = param.getAnnotation(TaskEvent.class);
                    if (userTaskEventAnnotation == null) {
                        return true;
                    }

                    parameters.add(methodParameterFactory
                            .getUserTaskEventParameter(userTaskEventAnnotation.value()));
                    return false;
                }).filter(param -> {
                    final var miTotalAnnotation = param.getAnnotation(MultiInstanceTotal.class);
                    if (miTotalAnnotation == null) {
                        return true;
                    }

                    parameters.add(methodParameterFactory
                            .getMultiInstanceTotalMethodParameter(miTotalAnnotation.value()));
                    return false;
                }).filter(param -> {
                    final var miIndexAnnotation = param.getAnnotation(MultiInstanceIndex.class);
                    if (miIndexAnnotation == null) {
                        return true;
                    }

                    parameters.add(methodParameterFactory
                            .getMultiInstanceIndexMethodParameter(miIndexAnnotation.value()));
                    return false;
                }).filter(param -> {
                    final var miElementAnnotation = param.getAnnotation(MultiInstanceElement.class);
                    if (miElementAnnotation == null) {
                        return true;
                    }

                    if (!miElementAnnotation.resolverBean().equals(NoResolver.class)) {

                        final var resolver = applicationContext
                                .getBean(miElementAnnotation.resolverBean());

                        parameters.add(methodParameterFactory
                                .getResolverBasedMultiInstanceMethodParameter(resolver));

                    } else if (!MultiInstanceElement.USE_RESOLVER.equals(miElementAnnotation.value())) {

                        parameters.add(methodParameterFactory
                                .getMultiInstanceElementMethodParameter(miElementAnnotation.value()));
                        
                    } else {
                        
                        throw new RuntimeException(
                                "Either attribute 'value' or 'resolver' of annotation @"
                                + MultiInstanceElement.class.getSimpleName()
                                + " has to be defined. Missing both at parameter "
                                + index[0]
                                + " of method "
                                + method);
                        
                    }
                    return false;
                }).forEach(param -> {
                    if (unknown.length() != 0) {
                        unknown.append(", ");
                    }
                    unknown.append(index[0]);
                    unknown.append(" (");
                    unknown.append(param.getType());
                    unknown.append(' ');
                    unknown.append(param.getName());
                    unknown.append(")");
                });

        if (unknown.length() != 0) {
            throw new RuntimeException(
                    "Unexpected parameter(s) in method '"
                    + method.getName()
                            + "': "
                    + unknown);
        }
        
        return Map.entry(method, parameters);

    }

    private Class<?> determineBeanClass(
            final Object bean) {
        
        final var proxyClass = bean.getClass();
        final var result = AopUtils.getTargetClass(bean);
        if (result != proxyClass) {
            return result;
        }
        return ClassUtils.getUserClass(bean);
        
    }
    
}
