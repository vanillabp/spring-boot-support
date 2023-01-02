package io.vanillabp.springboot.modules;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.util.LinkedList;
import java.util.List;

@AutoConfigurationPackage
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
            final var defaultsYaml = new ClassPathResource(
                    "/config/" + module.getWorkflowModuleId() + ".yaml");
            if (defaultsYaml.exists()) {
                logger.debug("Adding yaml-file: {}", defaultsYaml.getDescription());
                resources.add(defaultsYaml);
            }
            final var defaultsYml = new ClassPathResource(
                    "/config/" + module.getWorkflowModuleId() + ".yaml");
            if (defaultsYml.exists()) {
                logger.debug("Adding yaml-file: {}", defaultsYml.getDescription());
                resources.add(defaultsYml);
            }

            for (final var profile : environment.getActiveProfiles()) {
                final var rYaml = new ClassPathResource(
                        "/config/" + module.getWorkflowModuleId() + "-" + profile + ".yaml");
                if (rYaml.exists()) {
                    logger.debug("Adding yaml-file: {}", rYaml.getDescription());
                    resources.add(rYaml);
                }
                final var rYml = new ClassPathResource(
                        "/config/" + module.getWorkflowModuleId() + "-" + profile + ".yml");
                if (rYml.exists()) {
                    logger.debug("Adding yaml-file: {}", rYml.getDescription());
                    resources.add(rYml);
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

}
