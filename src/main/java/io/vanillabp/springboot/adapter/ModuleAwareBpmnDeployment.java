package io.vanillabp.springboot.adapter;

import io.vanillabp.springboot.modules.WorkflowModuleProperties;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static java.lang.String.format;

public abstract class ModuleAwareBpmnDeployment {

	public static final String DEFAULT_RESOURCES_PATH = "processes";
	
    protected abstract Logger getLogger();
    
    protected abstract String getAdapterId();
    
    private final VanillaBpProperties properties;

    @Autowired(required = false)
    private List<WorkflowModuleProperties> moduleProperties;
    
    public ModuleAwareBpmnDeployment(
            final VanillaBpProperties properties) {
        
        this.properties = properties;
        
    }
    
    protected void deployAllWorkflowModules() {
    	
    	if (moduleProperties == null) {
    		deployWorkflowModule(null);
    		return;
    	}
    	
    	moduleProperties.forEach(this::deployWorkflowModule);
    	
    }
    
    private void deployWorkflowModule(
    		final WorkflowModuleProperties propertySpecification) {
    	
        final var workflowModuleId = propertySpecification == null
                ? null
                : propertySpecification.getWorkflowModuleId();
        final var resourcesPath = resourcesPath(workflowModuleId);
    	
        deployWorkflowModule(
                workflowModuleId,
                resourcesPath);
    	
    }
    
    private String resourcesPath(
            final String workflowModuleId) {
                
        return properties
                .getAdapters()
                .entrySet()
                .stream()
                .filter(entry -> entry.getKey().equals(getAdapterId()))
                .findFirst()
                .map(entry -> entry.getValue().getResourcesPath())
                .or(() -> Optional.ofNullable(properties.getResourcesPath()))
                .orElse(DEFAULT_RESOURCES_PATH);

    }

    protected abstract void doDeployment(
    		String workflowModuleId,
    		Resource[] bpmns,
    		Resource[] dmns,
    		Resource[] cmms) throws Exception;

    private void deployWorkflowModule(
    		final String workflowModuleId,
    		final String basePackageName) {
    	
        try {

            final var bpmns = findResources(workflowModuleId, basePackageName, "*.bpmn");
            final var cmms = findResources(workflowModuleId, basePackageName, "*.cmmn");
            final var dmns = findResources(workflowModuleId, basePackageName, "*.dmn");

            doDeployment(
                    workflowModuleId,
                    bpmns,
                    dmns,
                    cmms);

            getLogger()
                    .info("Deployed resources for process archive <{}>",
                            workflowModuleId == null ? "default" : workflowModuleId);

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
        	throw new RuntimeException(e);
        }

    }

    private Resource[] findResources(
            final String workflowModuleId,
            final String basePackageName,
            final String fileNamePattern) throws IOException {

        final var resourcesPath = format("%s%s/**/%s",
                ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX,
                basePackageName.replace('.', '/'),
                fileNamePattern);

        getLogger()
                .debug("Scanning process archive <{}> for {}",
                        workflowModuleId == null ? "default" : workflowModuleId,
                        resourcesPath);

        return new PathMatchingResourcePatternResolver().getResources(resourcesPath);

    }

}
