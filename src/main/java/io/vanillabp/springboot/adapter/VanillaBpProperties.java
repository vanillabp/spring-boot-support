package io.vanillabp.springboot.adapter;

import io.vanillabp.springboot.utils.WorkflowAndModule;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "vanillabp", ignoreUnknownFields = true)
public class VanillaBpProperties {

    private List<String> defaultAdapter;
    
    private String resourcesPath;
    
    private List<WorkflowAndModuleAdapters> workflows = List.of();
    
    private Map<String, AdapterConfiguration> adapters = Map.of();
    
    public List<WorkflowAndModuleAdapters> getWorkflows() {
        return workflows;
    }
    
    public void setWorkflows(List<WorkflowAndModuleAdapters> workflows) {
        this.workflows = workflows;
    }
    
    public Map<String, AdapterConfiguration> getAdapters() {
        return adapters;
    }
    
    public void setAdapters(Map<String, AdapterConfiguration> adapters) {
        this.adapters = adapters;
    }
    
    public List<String> getDefaultAdapter() {
        return defaultAdapter;
    }
    
    public void setDefaultAdapter(List<String> defaultAdapter) {
        this.defaultAdapter = defaultAdapter;
    }
    
    public String getResourcesPath() {
        return resourcesPath;
    }
    
    public void setResourcesPath(String resourcesPath) {
        this.resourcesPath = resourcesPath;
    }
    
    public static class AdapterConfiguration {
        
        private String resourcesPath;
        
        public String getResourcesPath() {
            return resourcesPath;
        }
        
        public void setResourcesPath(String resourcesPath) {
            this.resourcesPath = resourcesPath;
        }
        
    }

    public static class WorkflowAndModuleAdapters extends WorkflowAndModule {
        
        private List<String> adapter = List.of();
        
        public List<String> getAdapter() {
            return adapter;
        }
        
        public void setAdapter(List<String> adapter) {
            this.adapter = adapter;
        }
        
    }

}
