package io.vanillabp.springboot.parameters;

import io.vanillabp.spi.service.MultiInstanceElementResolver;
import io.vanillabp.spi.service.TaskEvent;

public class MethodParameterFactory {

    public WorkflowAggregateMethodParameter getWorkflowAggregateMethodParameter(
            final String parameter) {

        return new WorkflowAggregateMethodParameter(parameter);

    }

    public MultiInstanceElementMethodParameter getMultiInstanceElementMethodParameter(
            final String parameter,
            final String name) {

        return new MultiInstanceElementMethodParameter(parameter, name);

    }

    public MultiInstanceIndexMethodParameter getMultiInstanceIndexMethodParameter(
            final String parameter,
            final String name) {

        return new MultiInstanceIndexMethodParameter(parameter, name);

    }

    public MultiInstanceTotalMethodParameter getMultiInstanceTotalMethodParameter(
            final String parameter,
            final String name) {

        return new MultiInstanceTotalMethodParameter(parameter, name);

    }

    public ResolverBasedMultiInstanceMethodParameter getResolverBasedMultiInstanceMethodParameter(
            final String parameter,
            final MultiInstanceElementResolver<?, ?> resolverBean) {

        return new ResolverBasedMultiInstanceMethodParameter(parameter, resolverBean);

    }
    
    public TaskParameter getTaskParameter(
            final String parameter,
            final String name) {
        
        return new TaskParameter(parameter, name);

    }
    
    public TaskIdMethodParameter getTaskIdParameter(
            final String parameter) {
        
        return new TaskIdMethodParameter(parameter);
        
    }

    public TaskEventMethodParameter getUserTaskEventParameter(
            final String parameter,
            final TaskEvent.Event[] events) {
        
        return new TaskEventMethodParameter(parameter, events);
        
    }
    
}
