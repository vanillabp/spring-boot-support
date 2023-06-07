package io.vanillabp.springboot.parameters;

import io.vanillabp.spi.service.MultiInstanceElementResolver;
import io.vanillabp.spi.service.TaskEvent;

public class MethodParameterFactory {

    public WorkflowAggregateMethodParameter getWorkflowAggregateMethodParameter(
            final int index,
            final String parameter) {

        return new WorkflowAggregateMethodParameter(index, parameter);

    }

    public MultiInstanceElementMethodParameter getMultiInstanceElementMethodParameter(
            final int index,
            final String parameter,
            final String name) {

        return new MultiInstanceElementMethodParameter(index, parameter, name);

    }

    public MultiInstanceIndexMethodParameter getMultiInstanceIndexMethodParameter(
            final int index,
            final String parameter,
            final String name) {

        return new MultiInstanceIndexMethodParameter(index, parameter, name);

    }

    public MultiInstanceTotalMethodParameter getMultiInstanceTotalMethodParameter(
            final int index,
            final String parameter,
            final String name) {

        return new MultiInstanceTotalMethodParameter(index, parameter, name);

    }

    public ResolverBasedMultiInstanceMethodParameter getResolverBasedMultiInstanceMethodParameter(
            final int index,
            final String parameter,
            final MultiInstanceElementResolver<?, ?> resolverBean) {

        return new ResolverBasedMultiInstanceMethodParameter(index, parameter, resolverBean);

    }
    
    public TaskParameter getTaskParameter(
            final int index,
            final String parameter,
            final String name) {
        
        return new TaskParameter(index, parameter, name);

    }
    
    public TaskIdMethodParameter getTaskIdParameter(
            final int index,
            final String parameter) {
        
        return new TaskIdMethodParameter(index, parameter);
        
    }

    public TaskEventMethodParameter getUserTaskEventParameter(
            final int index,
            final String parameter,
            final TaskEvent.Event[] events) {
        
        return new TaskEventMethodParameter(index, parameter, events);
        
    }
    
}
