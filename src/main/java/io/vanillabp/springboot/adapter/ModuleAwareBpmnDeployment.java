package io.vanillabp.springboot.adapter;

import static java.lang.String.format;

import io.vanillabp.springboot.modules.WorkflowModuleProperties;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public abstract class ModuleAwareBpmnDeployment {

	public static final String DEFAULT_BASE_PACKAGE_NAME = "processes";
	
    protected abstract Logger getLogger();

    @Value("${spring.application.name}")
    protected String applicationName;

    @Autowired(required = false)
    private List<WorkflowModuleProperties> moduleProperties;
    
    @Autowired(required = false)
    private List<BpDeploymentConfiguration> properties;
    
    protected void deployAllWorkflowModules() {
    	
    	if (moduleProperties == null) {
    		deployWorkflowModule(null);
    		return;
    	}
    	
    	moduleProperties.forEach(this::deployWorkflowModule);
    	
    }
    
    private void deployWorkflowModule(
    		final WorkflowModuleProperties propertySpecification) {
    	
        final var moduleProperties = moduleProperties(propertySpecification);
    	final var basePackageName = determineBasePackageName(moduleProperties);
    	final var workflowModuleId = propertySpecification == null
    	        ? applicationName
                : propertySpecification.getWorkflowModuleId();
    	
        deployWorkflowModule(
                workflowModuleId,
                basePackageName);
    	
    }
    
    private Optional<BpDeploymentConfiguration> moduleProperties(
            final WorkflowModuleProperties propertySpecification) {

        if (properties == null) {
            return Optional.empty();
        }
        
        if (propertySpecification == null) {
            if (properties.size() == 1) {
                return Optional.of(properties.get(0));
            }
            return Optional.empty();
        }
        
        return properties
                .stream()
                .filter(p -> propertySpecification.getPropertiesClass().isAssignableFrom(p.getClass()))
                .findFirst();
        
    }
    
    private String determineBasePackageName(
            final Optional<BpDeploymentConfiguration> moduleProperties) {
    	
    	if (moduleProperties.isEmpty()) {
    		return DEFAULT_BASE_PACKAGE_NAME;
    	}
    	
    	return moduleProperties
    	        .map(BpDeploymentConfiguration::getProcessesLocation)
    			.orElse(DEFAULT_BASE_PACKAGE_NAME);
    	
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

            doDeployment(workflowModuleId, bpmns, dmns, cmms);

            getLogger()
                    .info("Deployed resources for process archive <{}>", workflowModuleId);

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
                .debug("Scanning process archive <{}> for {}", workflowModuleId, resourcesPath);

        return new PathMatchingResourcePatternResolver().getResources(resourcesPath);

    }

}
