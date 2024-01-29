package io.vanillabp.springboot.adapter;

import io.vanillabp.spi.service.MultiInstanceElementResolver;
import io.vanillabp.spi.service.TaskEvent;
import io.vanillabp.springboot.adapter.wiring.WorkflowAggregateCache;
import io.vanillabp.springboot.parameters.MethodParameter;
import io.vanillabp.springboot.parameters.MultiInstanceElementMethodParameter;
import io.vanillabp.springboot.parameters.MultiInstanceIndexMethodParameter;
import io.vanillabp.springboot.parameters.MultiInstanceTotalMethodParameter;
import io.vanillabp.springboot.parameters.ResolverBasedMultiInstanceMethodParameter;
import io.vanillabp.springboot.parameters.TaskEventMethodParameter;
import io.vanillabp.springboot.parameters.TaskIdMethodParameter;
import io.vanillabp.springboot.parameters.TaskParameter;
import io.vanillabp.springboot.parameters.WorkflowAggregateMethodParameter;
import io.vanillabp.springboot.utils.MutableStream;
import org.slf4j.Logger;
import org.springframework.data.repository.CrudRepository;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public abstract class TaskHandlerBase {

    protected final CrudRepository<Object, Object> workflowAggregateRepository;

    protected final List<MethodParameter> parameters;

    protected final Object bean;

    protected final Method method;

    protected abstract Logger getLogger();

    public TaskHandlerBase(
            final CrudRepository<Object, Object> workflowAggregateRepository,
            final Object bean,
            final Method method,
            final List<MethodParameter> parameters) {
        
        this.workflowAggregateRepository = workflowAggregateRepository;
        this.bean = bean;
        this.method = method;
        this.parameters = parameters;


    }
    
    @SuppressWarnings("unchecked")
    protected <R> R execute(
            final WorkflowAggregateCache workflowAggregateCache,
            final Object workflowAggregateId,
            final boolean saveAggregateAfterwards,
            final BiFunction<Object[], MethodParameter, Boolean>... parameterProcessors)
            throws Exception {

        final var args = new Object[parameters.size()];
        
        // first, find domain entity as a parameter if required
        parameters
                .forEach(param -> processWorkflowAggregateParameter(
                        args, param, workflowAggregateCache, workflowAggregateId));
        
        // second, fill all the other parameters
        final var parameterStream = MutableStream
                .from(parameters.stream());
        
        Arrays
                .stream(parameterProcessors)
                .forEach(parameterProcessor -> {
                    parameterStream.apply(s ->
                            s.filter(param -> parameterProcessor.apply(args, param)));
                });
        
        parameterStream
                .getStream()
                // ignore unknown parameters, but they should be filtered as part of validation
                .forEach(param -> { /* */ });

        final R result;
        try {

            result = (R) method.invoke(bean, args);

        } catch (InvocationTargetException e) {

            final var targetException = e.getTargetException();
            if (targetException instanceof Exception) {
                throw (Exception) targetException;
            } else {
                throw new RuntimeException(e);
            }

        }

        if ((workflowAggregateCache.workflowAggregate != null)
                && saveAggregateAfterwards) {
            workflowAggregateCache.workflowAggregate =
                    workflowAggregateRepository
                            .save(workflowAggregateCache.workflowAggregate);
        }

        return result;
        
    }
    
    protected boolean processMultiInstanceTotalParameter(
            final Object[] args,
            final MethodParameter param,
            final Function<String, Object> multiInstanceSupplier) {

        if (!(param instanceof MultiInstanceTotalMethodParameter)) {
            return true;
        }

        args[param.getIndex()] = getMultiInstanceTotal(
                ((MultiInstanceTotalMethodParameter) param).getName(),
                multiInstanceSupplier);
        
        return false;
        
    }

    protected boolean processTaskEventParameter(
            final Object[] args,
            final MethodParameter param,
            final Supplier<TaskEvent.Event> taskEventSupplier) {

        if (!(param instanceof TaskEventMethodParameter)) {
            return true;
        }

        args[param.getIndex()] = taskEventSupplier.get();
        
        return false;
        
    }

    protected boolean processTaskIdParameter(
            final Object[] args,
            final MethodParameter param,
            final Supplier<String> taskIdSupplier) {

        if (!(param instanceof TaskIdMethodParameter)) {
            return true;
        }

        args[param.getIndex()] = taskIdSupplier.get();
        
        return false;
        
    }

    protected boolean processMultiInstanceIndexParameter(
            final Object[] args,
            final MethodParameter param,
            final Function<String, Object> multiInstanceSupplier) {

        if (!(param instanceof MultiInstanceIndexMethodParameter)) {
            return true;
        }

        args[param.getIndex()] = getMultiInstanceIndex(
                ((MultiInstanceIndexMethodParameter) param).getName(),
                multiInstanceSupplier);
        
        return false;
        
    }

    protected boolean processMultiInstanceElementParameter(
            final Object[] args,
            final MethodParameter param,
            final Function<String, Object> multiInstanceSupplier) {

        if (!(param instanceof MultiInstanceElementMethodParameter)) {
            return true;
        }
        
        args[param.getIndex()] = getMultiInstanceElement(
                ((MultiInstanceElementMethodParameter) param).getName(),
                multiInstanceSupplier);
        
        return false;
        
    }
    
    @SuppressWarnings("unchecked")
    protected MultiInstance<Object> getMultiInstance(final String name,
            final Function<String, Object> multiInstanceSupplier) {

        return (MultiInstance<Object>) multiInstanceSupplier.apply(name);

    }

    @SuppressWarnings("unchecked")
    protected Object getMultiInstanceElement(final String name, final Function<String, Object> multiInstanceSupplier) {

        return ((MultiInstance<Object>) multiInstanceSupplier.apply(name)).getElement();

    }

    @SuppressWarnings("unchecked")
    protected Integer getMultiInstanceTotal(final String name, final Function<String, Object> multiInstanceSupplier) {

        return ((MultiInstance<Object>) multiInstanceSupplier.apply(name)).getTotal();

    }

    @SuppressWarnings("unchecked")
    protected Integer getMultiInstanceIndex(
            final String name,
            final Function<String, Object> multiInstanceSupplier) {

        return ((MultiInstance<Object>) multiInstanceSupplier.apply(name)).getIndex();

    }

    protected boolean processMultiInstanceResolverParameter(
            final Object[] args,
            final MethodParameter param,
            final Supplier<Object> workflowAggregate,
            final Function<String, Object> multiInstanceSupplier) {

        if (!(param instanceof ResolverBasedMultiInstanceMethodParameter)) {
            return true;
        }
        
        @SuppressWarnings("unchecked")
        final var resolver =
                (MultiInstanceElementResolver<Object, Object>)
                ((ResolverBasedMultiInstanceMethodParameter) param).getResolverBean();

        final var multiInstances = new HashMap<String, MultiInstanceElementResolver.MultiInstance<Object>>();
        
        resolver
                .getNames()
                .forEach(name -> multiInstances.put(name, getMultiInstance(name, multiInstanceSupplier)));

        try {
            args[param.getIndex()] = resolver.resolve(workflowAggregate.get(), multiInstances);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed processing MultiInstanceElementResolver for parameter '"
                    + param.getParameter()
                    + "' of method '"
                    + method
                    + "'", e);
        }
        
        return false;
        
    }
    
    protected boolean processTaskParameter(
            final Object[] args,
            final MethodParameter param,
            final Function<String, Object> taskParameterSupplier) {
        
        if (!(param instanceof TaskParameter)) {
            return true;
        }
        
        args[param.getIndex()] = taskParameterSupplier.apply(
                ((TaskParameter) param).getName());
        
        return false;
        
    }

    protected boolean processWorkflowAggregateParameter(
            final Object[] args,
            final MethodParameter param,
            final WorkflowAggregateCache workflowAggregateCache,
            final Object workflowAggregateId) {

        if (!(param instanceof WorkflowAggregateMethodParameter)) {
            return true;
        }
        
        // Using findById is required to get an object instead of a Hibernate proxy.
        // Otherwise for e.g. Camunda8 connector JSON serialization of the
        // workflow aggregate is not possible.
        workflowAggregateCache.workflowAggregate = workflowAggregateRepository
                .findById(workflowAggregateId)
                .orElse(null);

        args[param.getIndex()] = workflowAggregateCache.workflowAggregate;

        return false;
        
    }
    
}
