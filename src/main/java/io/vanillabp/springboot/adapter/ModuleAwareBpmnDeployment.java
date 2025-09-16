package io.vanillabp.springboot.adapter;

import io.vanillabp.springboot.modules.WorkflowModuleProperties;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEvent;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.util.StringUtils;

public abstract class ModuleAwareBpmnDeployment {

    /**
     * Meant to be published after deployment of models
     */
    public static class BpmnModelCacheProcessed extends ApplicationEvent {

        private final String workflowModuleId;
        private final List<Map.Entry<String, String>> processedDeployed;

        public BpmnModelCacheProcessed(
                final Object source,
                final String workflowModuleId,
                final List<Map.Entry<String, String>> processedDeployed) {
            super(source);
            this.workflowModuleId = workflowModuleId;
            this.processedDeployed = processedDeployed;
        }

        public String getWorkflowModuleId() {
            return workflowModuleId;
        }

        /**
         * @return key = bpmnProcessId, value = versionInfo
         */
        public List<Map.Entry<String, String>> getProcessedDeployed() {
            return processedDeployed;
        }

    }

    protected abstract Logger getLogger();
    
    protected abstract String getAdapterId();

    // used to cache models which may be adopted by multiple adapters and deployed once the application started
    // key: workflow module ID, value entry of filename (key) and model (value)
    protected static final Map<String, Map.Entry<String, Object>> bpmnModelCache = new HashMap<>();
    // used to store properties which may be retrieved by multiple adapters to implement cross-cutting concerns
    protected static final Map<String, Object> adapterProperties = new HashMap<>();

    private final VanillaBpProperties properties;

    private final String applicationName;

    @Autowired(required = false)
    private List<WorkflowModuleProperties> moduleProperties;
    
    public ModuleAwareBpmnDeployment(
            final VanillaBpProperties properties,
            final String applicationName) {
        
        this.properties = properties;
        this.applicationName = applicationName;
        
    }

    protected void deployAllWorkflowModules() {

        deploySelectedWorkflowModules(
                moduleProperties == null
                        ? List.of()
                        : moduleProperties.stream().map(WorkflowModuleProperties::getWorkflowModuleId).toList());

    }

    protected void deploySelectedWorkflowModules(
            final Collection<String> workflowModuleIds) {

        final var hasExplicitDefinedWorkflowModules = (moduleProperties != null)
                && !moduleProperties.isEmpty();

        if (hasExplicitDefinedWorkflowModules) {
            moduleProperties
                    .stream()
                    .filter(module -> workflowModuleIds.contains(module.getWorkflowModuleId()))
                    .forEach(this::deployWorkflowModule);
            return;
        }

        if (!StringUtils.hasText(applicationName)) {
            throw new RuntimeException(
                    "No workflow-module configurations found (see "
                    + "https://github.com/vanillabp/spring-boot-support?tab=readme-ov-file#configuration)\n"
                    + "and need to use property 'spring.application.name' as the workflow-module-id instead but it is not defined!");
        } else {
            getLogger().info(
                    "No workflow-module configurations found (see "
                    + "https://github.com/vanillabp/spring-boot-support?tab=readme-ov-file#configuration),\n"
                    + "will use property 'spring.application.name' as the workflow-module-id instead: {}",
                    applicationName);
        }
        deployWorkflowModule(applicationName);
    	
    }
    
    private void deployWorkflowModule(
    		final WorkflowModuleProperties propertySpecification) {
    	
        final var workflowModuleId = propertySpecification.getWorkflowModuleId();

        deployWorkflowModule(
                workflowModuleId);
    	
    }

    protected abstract void doDeployment(
    		String workflowModuleId,
    		Resource[] bpmns,
    		Resource[] dmns,
    		Resource[] cmms) throws Exception;

    private void deployWorkflowModule(
    		final String workflowModuleId) {

        final var resourcesLocation = properties
                .getAdapterResourcesLocationFor(workflowModuleId, getAdapterId());

        try {

            final var bpmns = findResources(workflowModuleId, resourcesLocation, "*.bpmn");
            final var cmms = findResources(workflowModuleId, resourcesLocation, "*.cmmn");
            final var dmns = findResources(workflowModuleId, resourcesLocation, "*.dmn");

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
            final String adapterResourcesLocation,
            final String fileNamePattern) throws IOException {

        // test for multi-jar
        if ((moduleProperties != null)
                && (!adapterResourcesLocation.contains(":") // e.g. file://, classpath://, etc.
                || adapterResourcesLocation.startsWith(ResourcePatternResolver.CLASSPATH_URL_PREFIX))) {
            getLogger()
                    .warn("On using workflow modules you should define resource-path using '{}' to ensure BPMN resources are found!\nCurrent resource-path of module '{}' for adapater '{}':\n{}",
                            ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX,
                            workflowModuleId,
                            getAdapterId(),
                            adapterResourcesLocation);
        }

        final var resourcesLocation = adapterResourcesLocation.endsWith("/")
                ? adapterResourcesLocation + fileNamePattern
                : adapterResourcesLocation + "/" + fileNamePattern;

        getLogger()
                .debug("Scanning process archive <{}> for {}",
                        workflowModuleId == null ? "default" : workflowModuleId,
                        resourcesLocation);

        return new PathMatchingResourcePatternResolver().getResources(resourcesLocation);

    }

}
