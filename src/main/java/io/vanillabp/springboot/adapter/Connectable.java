package io.vanillabp.springboot.adapter;

public interface Connectable {

    String getBpmnProcessId();

    String getVersionInfo();

    boolean isExecutableProcess();

    String getTaskDefinition();
    
    String getElementId();

}
