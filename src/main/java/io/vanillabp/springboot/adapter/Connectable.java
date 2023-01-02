package io.vanillabp.springboot.adapter;

public interface Connectable {

    String getBpmnProcessId();

    boolean isExecutableProcess();

    String getTaskDefinition();
    
    String getElementId();

}
