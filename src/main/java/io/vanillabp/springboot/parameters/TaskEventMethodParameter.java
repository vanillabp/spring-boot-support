package io.vanillabp.springboot.parameters;

import io.vanillabp.spi.service.TaskEvent;
import io.vanillabp.spi.service.TaskEvent.Event;

import java.util.HashSet;
import java.util.Set;

public class TaskEventMethodParameter extends MethodParameter {

    private final Set<TaskEvent.Event> events;

    public TaskEventMethodParameter(
            final String parameter,
            final Event[] annotationParameter) {

        super(parameter);
        
        events = new HashSet<Event>();
        
        for (final var event : annotationParameter) {

            if (event == Event.ALL) {
                events.add(Event.CREATED);
                events.add(Event.CANCELED);
            } else {
                events.add(event);
            }

        }
        
    }

    public Set<TaskEvent.Event> getEvents() {

        return events;

    }
    
}
