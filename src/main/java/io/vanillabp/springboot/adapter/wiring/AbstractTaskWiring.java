package io.vanillabp.springboot.adapter.wiring;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import io.vanillabp.spi.service.BpmnProcess;
import io.vanillabp.spi.service.MultiInstanceElement;
import io.vanillabp.spi.service.MultiInstanceIndex;
import io.vanillabp.spi.service.MultiInstanceTotal;
import io.vanillabp.spi.service.NoResolver;
import io.vanillabp.spi.service.TaskParam;
import io.vanillabp.spi.service.WorkflowService;
import io.vanillabp.springboot.adapter.Connectable;
import io.vanillabp.springboot.parameters.MethodParameter;
import io.vanillabp.springboot.parameters.MethodParameterFactory;

public abstract class AbstractTaskWiring<T extends Connectable, A extends Annotation> {

    protected final ApplicationContext applicationContext;
    
    protected final MethodParameterFactory methodParameterFactory;
    
    public AbstractTaskWiring(
            final ApplicationContext applicationContext,
            final MethodParameterFactory methodParameterFactory) {

        this.applicationContext = applicationContext;
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
                    if (tested.length() > 0) {
                        tested.append(", ");
                    }
                    tested.append(m.getKey().toString());
                })
                .filter(m -> methodMatchesTaskDefinition.apply(m.getKey(), m.getValue())
                        || methodMatchesElementId.apply(m.getKey(), m.getValue()))
                .peek(m -> {
                    if (matching.length() > 0) {
                        matching.append(", ");
                    }
                    matching.append(m.getKey().toString());
                })
                .filter(m -> matchingMethods.getAndIncrement() == 0)
                .map(m -> Map.entry(m.getKey(), validateParameters.apply(m.getKey(), m.getValue())))
                .forEach(m -> connect.connect(bean, m.getKey(), m.getValue()));
        
    }
    
    protected boolean methodMatchesTaskDefinition(
            final T connectable,
            final Method method,
            final A annotation) {
        
        if (!StringUtils.hasText(connectable.getTaskDefinition())) {
            return false;
        }

        return false;
        
    }
    
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
        
        applicationContext
                .getBeansWithAnnotation(WorkflowService.class)
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
    
    protected boolean methodMatchesElementId(
            final T connectable,
            final Method method,
            final A annotation) {
        
        if (method.getName().equals(connectable.getElementId())) {
            return true;
        }

        return false;
        
    }
    
    protected List<MethodParameter> validateParameters(
            final Method method,
            @SuppressWarnings("unchecked") final BiFunction<Method, Parameter, MethodParameter>... map) {

        final var result = new LinkedList<MethodParameter>();
        final var unknown = new StringBuffer();

        if (!void.class.equals(method.getReturnType())) {
            throw new RuntimeException(
                    "Expected return-type 'void' for '"
                    + method
                    + "' but got: "
                    + method.getReturnType());
        }

        final var index = new AtomicInteger(-1);
        
        final var parameters = MutableParamterStream
                .fromMethod(method);
        
        parameters
                .apply(s -> s.peek(param -> index.incrementAndGet()));
        
        // apply all parameter filters
        Arrays
                .stream(map)
                .forEach(mapper -> {
                    parameters.apply(s -> s.filter(parameter -> {
                        final var mapped = mapper.apply(method, parameter);
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
                    if (unknown.length() != 0) {
                        unknown.append(", ");
                    }
                    unknown.append(index.get());
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
        
        return result;

    }

    protected MethodParameter validateWorkflowAggregateParameter(
            final Class<?> workflowAggregateClass,
            final Method method,
            final Parameter parameter) {
        
        final var isWorkflowAggregate = workflowAggregateClass.isAssignableFrom(
                parameter.getType());
        if (!isWorkflowAggregate) {
            return null;
        }

        return methodParameterFactory
                .getWorkflowAggregateMethodParameter(
                        parameter.getName());
        
    }

    protected MethodParameter validateTaskParam(
            final Method method,
            final Parameter parameter) {
        
        final var taskParamAnnotation = parameter.getAnnotation(TaskParam.class);
        if (taskParamAnnotation == null) {
            return null;
        }

        return methodParameterFactory
                .getTaskParameter(
                        parameter.getName(),
                        taskParamAnnotation.value());
        
    }

    protected MethodParameter validateMultiInstanceTotal(
            final Method method,
            final Parameter parameter) {
        
        final var miTotalAnnotation = parameter.getAnnotation(MultiInstanceTotal.class);
        if (miTotalAnnotation == null) {
            return null;
        }

        return methodParameterFactory
                .getMultiInstanceTotalMethodParameter(
                        parameter.getName(),
                        miTotalAnnotation.value());
        
    }

    protected MethodParameter validateMultiInstanceIndex(
            final Method method,
            final Parameter parameter) {

        final var miIndexAnnotation = parameter.getAnnotation(MultiInstanceIndex.class);
        if (miIndexAnnotation == null) {
            return null;
        }

        return methodParameterFactory
                .getMultiInstanceIndexMethodParameter(
                        parameter.getName(),
                        miIndexAnnotation.value());
        
    }
    
    protected MethodParameter validateMultiInstanceElement(
            final Method method,
            final Parameter parameter) {
        
        final var miElementAnnotation = parameter.getAnnotation(MultiInstanceElement.class);
        if (miElementAnnotation == null) {
            return null;
        }

        if (!miElementAnnotation.resolverBean().equals(NoResolver.class)) {

            final var resolver = applicationContext
                    .getBean(miElementAnnotation.resolverBean());

            return methodParameterFactory
                    .getResolverBasedMultiInstanceMethodParameter(
                            parameter.getName(),
                            resolver);

        } else if (!MultiInstanceElement.USE_RESOLVER.equals(miElementAnnotation.value())) {

            return methodParameterFactory
                    .getMultiInstanceElementMethodParameter(
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
    
}
