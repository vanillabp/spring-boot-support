package io.vanillabp.springboot.adapter.wiring;

import io.vanillabp.spi.service.BpmnProcess;
import io.vanillabp.spi.service.MultiInstanceElement;
import io.vanillabp.spi.service.MultiInstanceIndex;
import io.vanillabp.spi.service.MultiInstanceTotal;
import io.vanillabp.spi.service.NoResolver;
import io.vanillabp.spi.service.TaskParam;
import io.vanillabp.spi.service.WorkflowService;
import io.vanillabp.springboot.adapter.Connectable;
import io.vanillabp.springboot.adapter.SpringBeanUtil;
import io.vanillabp.springboot.parameters.MethodParameter;
import io.vanillabp.springboot.parameters.MethodParameterFactory;
import io.vanillabp.springboot.utils.MutableStream;
import io.vanillabp.springboot.utils.TriFunction;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

public abstract class AbstractTaskWiring<T extends Connectable, A extends Annotation, M extends MethodParameterFactory> {

    protected final ApplicationContext applicationContext;

    protected final SpringBeanUtil springBeanUtil;

    protected final M methodParameterFactory;
    
    public AbstractTaskWiring(
            final ApplicationContext applicationContext,
            final SpringBeanUtil springBeanUtil,
            final M methodParameterFactory) {

        this.applicationContext = applicationContext;
        this.springBeanUtil = springBeanUtil;
        this.methodParameterFactory = methodParameterFactory;
        
    }
    
    protected abstract Class<A> getAnnotationType();

    private void connectConnectableToBean(
            final T connectable,
            final StringBuilder tested,
            final StringBuilder matching,
            final AtomicInteger matchingMethods,
            final String beanName,
            final Object bean,
            final BiFunction<Method, A, Boolean> methodMatchesTaskDefinition,
            final BiFunction<Method, A, Boolean> methodMatchesElementId,
            final BiFunction<Method, A, List<MethodParameter>> validateParameters,
            final ConnectBean connect) {
        
        final Class<?> beanClass = determineBeanClass(bean);
        
        Arrays
                .stream(beanClass.getMethods())
                .flatMap(method -> Arrays
                        .stream(method.getAnnotationsByType(getAnnotationType()))
                        .map(annotation -> Map.entry(method, annotation)))
                .peek(m -> {
                    if (!tested.isEmpty()) {
                        tested.append(", ");
                    }
                    tested.append(m.getKey().toString());
                })
                .filter(m -> methodMatchesTaskDefinition.apply(m.getKey(), m.getValue())
                        || methodMatchesElementId.apply(m.getKey(), m.getValue()))
                .peek(m -> {
                    if (!matching.isEmpty()) {
                        matching.append(", ");
                    }
                    matching.append(m.getKey().toString());
                })
                .filter(m -> matchingMethods.getAndIncrement() == 0)
                .map(m -> Map.entry(m.getKey(), validateParameters.apply(m.getKey(), m.getValue())))
                .forEach(m -> connect.connect(bean, m.getKey(), m.getValue()));
        
    }

    protected abstract boolean methodMatchesElementId(
            final T connectable,
            final Method method,
            final A annotation);

    protected abstract boolean methodMatchesTaskDefinition(
            final T connectable,
            final Method method,
            final A annotation);
    
    protected boolean wireTask(
            final T connectable,
            final boolean allowNoMethodFound,
            final BiFunction<Method, A, Boolean> methodMatchesTaskDefinition,
            final BiFunction<Method, A, Boolean> methodMatchesElementId,
            final BiFunction<Method, A, List<MethodParameter>> validateParameters,
            final ConnectBean connect) {
        
        final var tested = new StringBuilder();
        final var matching = new StringBuilder();
        final var matchingMethods = new AtomicInteger(0);

        springBeanUtil
                .getWorkflowAnnotatedBeans()
                .entrySet()
                .stream()
                .filter(bean -> isAboutConnectableProcess(
                        connectable.getBpmnProcessId(),
                        bean.getValue()))
                .forEach(bean -> {
                    connectConnectableToBean(
                            connectable,
                            tested,
                            matching,
                            matchingMethods,
                            bean.getKey(),
                            bean.getValue(),
                            methodMatchesTaskDefinition,
                            methodMatchesElementId,
                            validateParameters,
                            connect);
                });

        if (matchingMethods.get() > 1) {
            throw new RuntimeException(
                    "More than one method annotated with @"
                    + getAnnotationType().getName()
                    + " is matching task having "
                    + (StringUtils.hasText(connectable.getTaskDefinition())
                            ? "task-definition '" + connectable.getTaskDefinition()
                            : "element-id '" + connectable.getElementId())
                    + "' of process '"
                    + connectable.getBpmnProcessId()
                    + "' of version '"
                    + connectable.getVersionInfo()
                    + "': "
                    + matching);
        }
        
        if (matchingMethods.get() == 0) {
            if (allowNoMethodFound) {
                return false;
            }
            
            throw new RuntimeException(
                    "No public method annotated with @"
                    + getAnnotationType().getName()
                    + " is matching task having "
                    + (StringUtils.hasText(connectable.getTaskDefinition())
                            ? "task-definition '" + connectable.getTaskDefinition()
                            : "no task-definition but element-id '" + connectable.getElementId())
                    + "' of process '"
                    + connectable.getBpmnProcessId()
                    + "' of version '"
                    + connectable.getVersionInfo()
                    + "'. Tested for: "
                    + tested);
        }
        
        return true;

    }
    
    protected boolean isAboutConnectableProcess(
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

    protected Class<?> determineBeanClass(
            final Object bean) {
        
        final var proxyClass = bean.getClass();
        final var result = AopUtils.getTargetClass(bean);
        if (result != proxyClass) {
            return result;
        }
        return ClassUtils.getUserClass(bean);
        
    }

    protected List<MethodParameter> validateParameters(
            final Method method,
            @SuppressWarnings("unchecked") final TriFunction<Method, Parameter, Integer, MethodParameter>... map) {

        final var result = new LinkedList<MethodParameter>();
        final var unknown = new StringBuffer();

        final var index = new AtomicInteger(-1);
        
        final var parameters = MutableStream
                .from(Arrays.stream(method.getParameters()));
        
        parameters
                .apply(s -> s.peek(param -> index.incrementAndGet()));
        
        // apply all parameter filters
        Arrays
                .stream(map)
                .forEach(mapper -> {
                    parameters.apply(s -> s.filter(parameter -> {
                        final var mapped = mapper.apply(method, parameter, index.get());
                        if (mapped == null) {
                            return true;
                        }
                        result.add(mapped);
                        return false;
                    }));
                });
        
        parameters
                .getStream()
                .forEach(param -> {
                    if (!unknown.isEmpty()) {
                        unknown.append(", ");
                    }
                    unknown.append(index.get());
                    unknown.append(" (");
                    unknown.append(param.getType());
                    unknown.append(' ');
                    unknown.append(param.getName());
                    unknown.append(")");
                });
        
        if (!unknown.isEmpty()) {
            throw new RuntimeException(
                    "Unexpected parameter(s) in method '"
                    + method.getName()
                            + "': "
                    + unknown);
        }
        
        return result;

    }

    protected MethodParameter validateWorkflowAggregateParameter(
            final Class<?> workflowAggregateClass,
            final Method method,
            final Parameter parameter,
            final int index) {
        
        if (workflowAggregateClass == null) {
            return null;
        }
        
        final var isWorkflowAggregate = workflowAggregateClass.isAssignableFrom(
                parameter.getType());
        if (!isWorkflowAggregate) {
            return null;
        }

        return methodParameterFactory
                .getWorkflowAggregateMethodParameter(
                        index,
                        parameter.getName());
        
    }

    protected MethodParameter validateTaskParam(
            final Method method,
            final Parameter parameter,
            final int index) {
        
        final var taskParamAnnotation = parameter.getAnnotation(TaskParam.class);
        if (taskParamAnnotation == null) {
            return null;
        }

        return methodParameterFactory
                .getTaskParameter(
                        index,
                        parameter.getName(),
                        taskParamAnnotation.value());
        
    }

    protected MethodParameter validateMultiInstanceTotal(
            final Method method,
            final Parameter parameter,
            final int index) {
        
        final var miTotalAnnotation = parameter.getAnnotation(MultiInstanceTotal.class);
        if (miTotalAnnotation == null) {
            return null;
        }

        return methodParameterFactory
                .getMultiInstanceTotalMethodParameter(
                        index,
                        parameter.getName(),
                        miTotalAnnotation.value());
        
    }

    protected MethodParameter validateMultiInstanceIndex(
            final Method method,
            final Parameter parameter,
            final int index) {

        final var miIndexAnnotation = parameter.getAnnotation(MultiInstanceIndex.class);
        if (miIndexAnnotation == null) {
            return null;
        }

        return methodParameterFactory
                .getMultiInstanceIndexMethodParameter(
                        index,
                        parameter.getName(),
                        miIndexAnnotation.value());
        
    }
    
    protected MethodParameter validateMultiInstanceElement(
            final Method method,
            final Parameter parameter,
            final int index) {
        
        final var miElementAnnotation = parameter.getAnnotation(MultiInstanceElement.class);
        if (miElementAnnotation == null) {
            return null;
        }

        if (!miElementAnnotation.resolverBean().equals(NoResolver.class)) {

            final var resolver = applicationContext
                    .getBean(miElementAnnotation.resolverBean());

            return methodParameterFactory
                    .getResolverBasedMultiInstanceMethodParameter(
                            index,
                            parameter.getName(),
                            resolver);

        } else if (!MultiInstanceElement.USE_RESOLVER.equals(miElementAnnotation.value())) {

            return methodParameterFactory
                    .getMultiInstanceElementMethodParameter(
                            index,
                            parameter.getName(),
                            miElementAnnotation.value());
            
        } else {
            
            throw new RuntimeException(
                    "Either attribute 'value' or 'resolver' of annotation @"
                    + MultiInstanceElement.class.getSimpleName()
                    + " has to be defined. Missing both at parameter "
                    + parameter.getName()
                    + " of method "
                    + method);
            
        }
        
    }
    
    protected Map.Entry<Class<?>, Class<?>> determineAndValidateWorkflowAggregateAndServiceClass(
            final String bpmnProcessId) {

        final var tested = new StringBuilder();
        
        final var matchingServices = springBeanUtil
                .getWorkflowAnnotatedBeans()
                .entrySet()
                .stream()
                .peek(bean -> {
                    if (!tested.isEmpty()) {
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

        if (matchingServices.isEmpty()) {
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
                        if (!found.isEmpty()) {
                            found.append("; ");
                        }
                        found.append(entry.getKey().getName());
                        found.append(" by ");
                    })
                    .flatMap(entry -> entry.getValue().stream())
                    .forEach(matchingService -> {
                        if (!found.isEmpty()) {
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

    protected boolean isPrimaryProcessWiring(
            final String workflowModuleId,
            final String bpmnProcessId,
            final Class<?> workflowServiceClass) {

        final var primaryBpmnProcessIds = Arrays
                .stream(workflowServiceClass.getAnnotationsByType(WorkflowService.class))
                .map(WorkflowService::bpmnProcess)
                .map(bpmnProcess -> bpmnProcess.bpmnProcessId().equals(BpmnProcess.USE_CLASS_NAME)
                        ? workflowServiceClass.getSimpleName()
                        : bpmnProcess.bpmnProcessId())
                .toList();
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
    
}
