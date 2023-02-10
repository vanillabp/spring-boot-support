package io.vanillabp.springboot.adapter;

import io.vanillabp.springboot.utils.WorkflowAndModule;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.lang.NonNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "vanillabp", ignoreUnknownFields = true)
public class VanillaBpProperties {

    @NonNull
    private String defaultAdapter;
    
    private String resourcesPath;
    
    private Map<String, AdapterConfiguration> adapters = new HashMap<>();
    
    public Map<String, AdapterConfiguration> getAdapters() {
        return adapters;
    }
    
    public void setAdapters(Map<String, AdapterConfiguration> adapters) {
        this.adapters = adapters;
    }
    
    public String getDefaultAdapter() {
        return defaultAdapter;
    }
    
    public void setDefaultAdapter(String defaultAdapter) {
        this.defaultAdapter = defaultAdapter;
    }
    
    public String getResourcesPath() {
        return resourcesPath;
    }
    
    public void setResourcesPath(String resourcesPath) {
        this.resourcesPath = resourcesPath;
    }
    
    public static class AdapterConfiguration {
        
        private List<WorkflowAndModule> adapterFor = List.of();
        
        private String resourcesPath;

        public List<WorkflowAndModule> getAdapterFor() {
            return adapterFor;
        }

        public void setAdapterFor(List<WorkflowAndModule> adapterFor) {
            this.adapterFor = adapterFor;
        }
        
        public String getResourcesPath() {
            return resourcesPath;
        }
        
        public void setResourcesPath(String resourcesPath) {
            this.resourcesPath = resourcesPath;
        }
        
    }
    
}
