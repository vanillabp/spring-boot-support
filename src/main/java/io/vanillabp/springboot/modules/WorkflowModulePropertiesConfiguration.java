package io.vanillabp.springboot.modules;

import io.vanillabp.springboot.utils.CaseUtils;
import java.util.LinkedList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

@AutoConfigurationPackage
@AutoConfigureBefore(PropertyPlaceholderAutoConfiguration.class)
@ConditionalOnBean(WorkflowModuleProperties.class)
public class WorkflowModulePropertiesConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowModulePropertiesConfiguration.class);

    /**
     * @see https://stackoverflow.com/questions/35197175/spring-what-is-the-programmatic-equivalent-of-propertysource
     */
    @Bean
    public static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer(
            final Environment environment,
            final List<WorkflowModuleProperties> modules) {

        final var resources = new LinkedList<Resource>();
        for (final var module : modules) {
            
            // add e.g. taxiRide.yml or taxiRide.yaml
            if (!addYaml(resources, module.getWorkflowModuleId())) {
                // or taxi-ride.yml or taxi-ride.yaml
                addYaml(resources,
                        CaseUtils.camelToKebap(module.getWorkflowModuleId()));
            }

            for (final var profile : environment.getActiveProfiles()) {

                // add e.g. taxiRide-local.yml or taxiRide-local.yaml
                if (!addYaml(resources, module.getWorkflowModuleId() + "-" + profile)) {
                    // or taxi-ride-local.yml or taxi-ride-local.yaml
                    addYaml(resources,
                            CaseUtils.camelToKebap(module.getWorkflowModuleId()) + "-" + profile);
                }

            }

        }

        final var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(resources.toArray(Resource[]::new));
        yaml.afterPropertiesSet();

        final var ppc = new PropertySourcesPlaceholderConfigurer();
        ppc.setProperties(yaml.getObject());
        ppc.setEnvironment(environment);
        return ppc;

    }

    private static boolean addYaml(
            final LinkedList<Resource> resources,
            final String directory,
            final String formatedWorkflowModuleId) {

        final var defaultsYaml = new ClassPathResource(
                directory + formatedWorkflowModuleId + ".yaml");
        if (defaultsYaml.exists()) {
            logger.debug("Adding yaml-file: {}", defaultsYaml.getDescription());
            resources.add(defaultsYaml);
            return true;
        }
        
        final var defaultsYml = new ClassPathResource(
                directory + formatedWorkflowModuleId + ".yml");
        if (defaultsYml.exists()) {
            logger.debug("Adding yaml-file: {}", defaultsYml.getDescription());
            resources.add(defaultsYml);
            return true;
        }
        
        return false;
        
    }

    private static boolean addYaml(
            final LinkedList<Resource> resources,
            final String formatedWorkflowModuleId) {

        if (addYaml(resources, "", formatedWorkflowModuleId)) {
            return true;
        }
        if (addYaml(resources, "config/", formatedWorkflowModuleId)) {
            return true;
        }
        if (addYaml(resources, formatedWorkflowModuleId + "/", formatedWorkflowModuleId)) {
            return true;
        }
        if (addYaml(resources, formatedWorkflowModuleId + "/config/", formatedWorkflowModuleId)) {
            return true;
        }
        return false;

    }

}
