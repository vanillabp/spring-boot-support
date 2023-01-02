package io.vanillabp.springboot.modules;

import io.vanillabp.springboot.utils.CaseUtils;

public class WorkflowModuleProperties implements WorkflowModuleIdAwareProperties {
	
    private Class<?> propertiesClass;
	
    private String moduleId;

    public WorkflowModuleProperties(
            final Class<? extends WorkflowModuleIdAwareProperties> propertiesClass,
    		final String moduleId) {
    	
    	this.propertiesClass = propertiesClass;
        this.moduleId = moduleId;
        
    }

    public WorkflowModuleProperties(
            final Class<? extends WorkflowModuleIdAwareProperties> propertiesClass,
    		final String moduleId,
    		final boolean isCamelCase) {
    	
    	this.propertiesClass = propertiesClass;
        if (isCamelCase) {
            this.moduleId = CaseUtils.camelToKebap(moduleId);
        } else {
            this.moduleId = moduleId;
        }
        
    }

    @Override
    public String getWorkflowModuleId() {
        
        return moduleId;
        
    }
    
    public Class<?> getPropertiesClass() {
		
    	return propertiesClass;
    	
	}
    
}