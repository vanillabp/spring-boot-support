package io.vanillabp.springboot.modules;

public class WorkflowModuleProperties implements WorkflowModuleIdAwareProperties {
	
    private Class<?> propertiesClass;
	
    private String moduleId;

    public WorkflowModuleProperties(
            final Class<? extends WorkflowModuleIdAwareProperties> propertiesClass,
    		final String moduleId) {
    	
    	this.propertiesClass = propertiesClass;
        this.moduleId = moduleId;
        
    }

    @Override
    public String getWorkflowModuleId() {
        
        return moduleId;
        
    }
    
    public Class<?> getPropertiesClass() {
		
    	return propertiesClass;
    	
	}
    
}