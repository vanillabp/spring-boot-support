package io.vanillabp.springboot.adapter;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@ConfigurationProperties(prefix = VanillaBpProperties.PREFIX, ignoreUnknownFields = true)
public class VanillaBpProperties {

    private static final Logger logger = LoggerFactory.getLogger(VanillaBpProperties.class);

    public static final String PREFIX = "vanillabp";

    private List<String> defaultAdapter = new ArrayList<>();

    private Map<String, WorkflowModuleAdapterProperties> workflowModules = new LinkedHashMap<>();

    public Map<String, WorkflowModuleAdapterProperties> getWorkflowModules() {
        return workflowModules;
    }

    public void setWorkflowModules(Map<String, WorkflowModuleAdapterProperties> workflowModules) {

        this.workflowModules = workflowModules;

    }

    /**
     * Links every nested properties object to its parent and injects the map key as its id.
     * <p>
     * This used to be a side effect of the setters, which made correctness depend on how often and in
     * which order the configuration-properties binder calls them. Doing it once after binding is
     * complete is independent of the binder's strategy, and it also covers nested workflows, whose
     * enclosing objects are not Spring beans and therefore have no lifecycle callback of their own.
     * <p>
     * Idempotent on purpose, so a rebind can simply run it again.
     */
    @PostConstruct
    public void initializeHierarchy() {

        workflowModules.forEach((workflowModuleId, workflowModule) -> {
            workflowModule.workflowModuleId = workflowModuleId;
            workflowModule.defaultProperties = this;
            workflowModule.getWorkflows().forEach((bpmnProcessId, workflow) -> {
                workflow.bpmnProcessId = bpmnProcessId;
                workflow.workflowModule = workflowModule;
            });
        });

    }

    public List<String> getDefaultAdapter() { return defaultAdapter; }

    public void setDefaultAdapter(List<String> defaultAdapter) { this.defaultAdapter = defaultAdapter; }

    public static class AdapterConfiguration {
        
        private String resourcesLocation;
        
        public String getResourcesLocation() {
            return resourcesLocation;
        }
        
        public void setResourcesLocation(String resourcesLocation) {
            this.resourcesLocation = resourcesLocation;
        }
        
    }

    public static class WorkflowModuleAdapterProperties extends AdapterProperties {

        String workflowModuleId;

        VanillaBpProperties defaultProperties;

        private Map<String, AdapterConfiguration> adapters = new LinkedHashMap<>();

        private Map<String, WorkflowAdapterProperties> workflows = new LinkedHashMap<>();

        public Map<String, WorkflowAdapterProperties> getWorkflows() {
            return workflows;
        }

        public void setWorkflows(Map<String, WorkflowAdapterProperties> workflows) {

            // back-links are established centrally, see VanillaBpProperties#initializeHierarchy
            this.workflows = workflows;

        }

        public Map<String, AdapterConfiguration> getAdapters() {
            return adapters;
        }

        public void setAdapters(Map<String, AdapterConfiguration> adapters) {
            this.adapters = adapters;
        }

        public String getWorkflowModuleId() {
            return workflowModuleId;
        }

        public VanillaBpProperties getDefaultProperties() {
            return defaultProperties;
        }

    }

    public static class WorkflowAdapterProperties extends AdapterProperties {

        String bpmnProcessId;

        WorkflowModuleAdapterProperties workflowModule;

        public WorkflowModuleAdapterProperties getWorkflowModule() {
            return workflowModule;
        }

        public String getBpmnProcessId() {
            return bpmnProcessId;
        }

    }

    private static class AdapterProperties {

        private List<String> defaultAdapter = new ArrayList<>();

        public List<String> getDefaultAdapter() {
            return defaultAdapter;
        }

        public void setDefaultAdapter(List<String> defaultAdapter) {
            this.defaultAdapter = defaultAdapter;
        }

    }

    public List<String> getDefaultAdapterFor(
            final String workflowModuleId,
            final String bpmnProcessId) {

        var defaultAdapter = getDefaultAdapter();
        final var workflowModule = getWorkflowModules().get(workflowModuleId);
        if (workflowModule != null) {
            if (!workflowModule.getDefaultAdapter().isEmpty()) {
                defaultAdapter = workflowModule.getDefaultAdapter();
            }
            if (bpmnProcessId != null) {
                final var workflow = workflowModule.getWorkflows().get(bpmnProcessId);
                if (workflow != null) {
                    if (!workflow.getDefaultAdapter().isEmpty()) {
                        defaultAdapter = workflowModule.getDefaultAdapter();
                    }
                }
            }
        }
        return defaultAdapter;

    }

    public String getAdapterResourcesLocationFor(
            final String workflowModuleId,
            final String adapterId) {

        String resourcesLocation = null;
        final var workflowModule = getWorkflowModules().get(workflowModuleId);
        if (workflowModule != null) {
            final var adapter = workflowModule.getAdapters().get(adapterId);
            if (adapter != null) {
                resourcesLocation = adapter.getResourcesLocation();
            }
        }
        if (resourcesLocation == null) {
            throw new RuntimeException(
                    "Property '"
                    + VanillaBpProperties.PREFIX
                    + ".workflow-modules."
                    + workflowModuleId
                    + ".adapters."
                    + adapterId
                    + ".resources-location' not set!\nIt has to point to a location specific to the adapter in order "
                    + "to avoid future problems once you wish to migrate to another adapter.\nSample: '"
                    + "classpath*:/workflow-resources/"
                    + adapterId
                    + "'");
        }
        return resourcesLocation;

    }

    public void validatePropertiesFor(
            final List<String> adapterIds,
            final String workflowModuleId,
            final String bpmnProcessId) {

        final var defaultAdapters = getDefaultAdapterFor(workflowModuleId, bpmnProcessId);
        if (defaultAdapters.isEmpty()) {
            throw new RuntimeException(
                    "More than one VanillaBP adapter was found in classpath, but no default adapter is configured at\n  "
                            + PREFIX
                            + ".workflow-modules."
                            + workflowModuleId
                            + ".workflows."
                            + bpmnProcessId
                            + ".default-adapter or \n  "
                            + PREFIX
                            + ".workflow-modules."
                            + workflowModuleId
                            + ".default-adapter or \n  "
                            + PREFIX
                            + ".default-adapter\nAvailable adapters are '"
                            + String.join("', '", adapterIds)
                            + "'.");
        }

        final var listOfAdapters = String.join("', '", adapterIds);
        final var missingAdapters = defaultAdapters
                .stream()
                .filter(defaultAdapter -> !adapterIds.contains(defaultAdapter))
                .collect(Collectors.joining("', '"));
        if (!missingAdapters.isEmpty()) {
            throw new RuntimeException(
                    "Property 'default-adapter' of workflow-module '"
                    + workflowModuleId
                    + "' and bpmn-process-id '"
                    + bpmnProcessId
                    + "' contains adapters not available in classpath:\n'  "
                    + missingAdapters
                    + "'!\nAvailable adapters are: '"
                    + listOfAdapters
                    + "'.");
        }

    }

    public void validateProperties(
            final List<String> adapterIds,
            final List<String> knownWorkflowModuleIds) {

        // unknown workflow-module properties
        final var configAvailableButNotInClasspath = new LinkedList<>(
                getWorkflowModules()
                .keySet());
        configAvailableButNotInClasspath
                .removeAll(knownWorkflowModuleIds);
        if (!configAvailableButNotInClasspath.isEmpty()) {
            logger.warn("Found properties for workflow-modules\n"
                    + PREFIX + ".workflow-modules."
                    + String.join(
                            "\n" + PREFIX + ".workflow-modules.",
                            configAvailableButNotInClasspath)
                    + "\nwhich were not found in the class-path!");
        }

        // adapter configured
        if (adapterIds.size() == 1) {
            logger.info(
                    "Found only one VanillaBP adapter '"
                    + adapterIds.get(0)
                    + "' in classpath. Please ensure the properties\n"
                    + PREFIX
                    + ".workflow-modules"
                    + String.join(
                            ".adapters."
                                    + adapterIds.get(0)
                                    + ".resources-location\n"
                                    + PREFIX
                                    + ".workflow-modules",
                            configAvailableButNotInClasspath)
                    + ".adapters."
                    + adapterIds.get(0)
                    + ".resources-location\nare specific for this adapter in " +
                    "order to avoid future-problems once you wish to migrate to another adapter.");
        }

        // adapters in class-path not used
        final var notConfiguredAdapters = new HashMap<String, Set<String>>() {
                @Override
                public Set<String> get(final Object key) {
                    var adapters = super.get(key);
                    if (adapters == null) {
                        adapters = new HashSet<>();
                        super.put(key.toString(), adapters);
                    }
                    return adapters;
                }
            };
        getDefaultAdapter()
                .stream()
                .filter(adapterId -> !adapterIds.contains(adapterId))
                .forEach(adapterId -> notConfiguredAdapters.get(
                VanillaBpProperties.PREFIX
                        + ".default-adapter"
                ).add(adapterId));
        getWorkflowModules().values().forEach(workflowModule -> {
                workflowModule
                        .getDefaultAdapter()
                        .stream()
                        .filter(adapterId -> !adapterIds.contains(adapterId))
                        .forEach(adapterId -> notConfiguredAdapters.get(
                                VanillaBpProperties.PREFIX
                                        + ".workflow-modules."
                                        + workflowModule.workflowModuleId
                                        + ".default-adapter"
                        ).add(adapterId));
                workflowModule.getWorkflows().values().forEach(workflow -> {
                    workflow
                            .getDefaultAdapter()
                            .stream()
                            .filter(adapterId -> !adapterIds.contains(adapterId))
                            .forEach(adapterId -> notConfiguredAdapters.get(
                            VanillaBpProperties.PREFIX
                                    + ".workflow-modules."
                                    + workflowModule.workflowModuleId
                                    + ".workflows."
                                    + workflow.getBpmnProcessId()
                                    + ".default-adapter"
                            ).add(adapterId));
                });
            });
        if (!notConfiguredAdapters.isEmpty()) {
            throw new RuntimeException(
                    "There are VanillaBP adapters configured not found in classpath:\n"
                            + notConfiguredAdapters
                            .entrySet()
                            .stream()
                            .map(entry -> "  " + entry.getKey() + "=" + entry.getValue().stream().collect(Collectors.joining(",")))
                            .collect(Collectors.joining("\n")));
        }

        // resources-location
        knownWorkflowModuleIds
                .forEach(workflowModuleId -> {
                    final var defaultAdapterOfModule = getDefaultAdapterFor(workflowModuleId, null);
                    final var resourcesLocationOfModule = (defaultAdapterOfModule.isEmpty()
                                    ? defaultAdapter : defaultAdapterOfModule)
                            .stream()
                            .filter(adapterId -> getAdapterResourcesLocationFor(workflowModuleId, adapterId) == null)
                            .toList();
                    if (!resourcesLocationOfModule.isEmpty()) {
                        throw new RuntimeException(
                                "You need to define properties '"
                                        + PREFIX
                                        + ".workflow-modules."
                                        + workflowModuleId
                                        + ".adapters."
                                        + String.join(
                                        ".resources-location\n"
                                                + PREFIX
                                                + ".workflow-modules."
                                                + workflowModuleId
                                                + ".adapters.",
                                        defaultAdapter)
                                        + ".resources-location");
                    }
                    getBpmnProcessIdsForWorkflowModule(workflowModuleId)
                            .forEach(bpmnProcessId -> {
                                final var defaultAdapter = getDefaultAdapterFor(workflowModuleId, bpmnProcessId);
                                final var resourcesLocation = defaultAdapter
                                        .stream()
                                        .filter(adapterId -> getAdapterResourcesLocationFor(workflowModuleId, adapterId) == null)
                                        .toList();
                                if (!resourcesLocation.isEmpty()) {
                                    throw new RuntimeException(
                                            "You need to define properties '"
                                                    + PREFIX
                                                    + ".workflow-modules."
                                                    + workflowModuleId
                                                    + ".adapters."
                                                    + String.join(
                                                    ".resources-location\n"
                                                            + PREFIX
                                                            + ".workflow-modules."
                                                            + workflowModuleId
                                                            + ".adapters.",
                                                    defaultAdapter)
                                                    + ".resources-location");
                                }
                            });
                });

    }

    private List<String> getBpmnProcessIdsForWorkflowModule(
            final String workflowModuleId) {

        final var workflowModule = getWorkflowModules().get(workflowModuleId);
        if (workflowModule == null) {
            return List.of();
        }

        return workflowModule
                .getWorkflows()
                .values()
                .stream()
                .map(WorkflowAdapterProperties::getBpmnProcessId)
                .toList();

    }

}
