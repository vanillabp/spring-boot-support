package io.vanillabp.springboot.modules;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Regression net for {@link WorkflowModulePropertiesConfiguration}. Written against Spring Boot 3 to
 * document the current behaviour before the Spring Boot 4 migration.
 *
 * <p>Two mechanisms are at risk here. First, the configuration class is registered as an
 * auto-configuration but carries neither {@code @Configuration} nor {@code @AutoConfiguration} - it
 * relies on configuration-class inference. Second, the {@code PropertySourcesPlaceholderConfigurer}
 * is produced by a <em>static</em> {@code @Bean} method that takes beans as parameters, which forces
 * those beans to be created during the bean-factory-post-processor phase.
 *
 * <p>If either mechanism stops working, module yaml files are silently not loaded: placeholders
 * referring to them fail to resolve, which surfaces far away from the cause.
 *
 * <p>Test resources used: {@code testModule.yaml}, {@code testModule-unittest.yaml},
 * {@code config/kebab-module.yaml}.
 */
class WorkflowModulePropertiesConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(WorkflowModulePropertiesConfiguration.class));

    @Configuration
    static class SingleModule {

        @Bean
        WorkflowModuleProperties testModule() {
            return new WorkflowModuleProperties(WorkflowModuleIdAwareProperties.class, "testModule");
        }

    }

    @Configuration
    static class KebabModule {

        // camelToKebap("kebabModule") -> "kebab-module", resolved from config/kebab-module.yaml
        @Bean
        WorkflowModuleProperties kebabModule() {
            return new WorkflowModuleProperties(WorkflowModuleIdAwareProperties.class, "kebabModule");
        }

    }

    @Configuration
    static class TwoModules {

        @Bean
        WorkflowModuleProperties testModule() {
            return new WorkflowModuleProperties(WorkflowModuleIdAwareProperties.class, "testModule");
        }

        @Bean
        WorkflowModuleProperties kebabModule() {
            return new WorkflowModuleProperties(WorkflowModuleIdAwareProperties.class, "kebabModule");
        }

    }

    /** Resolves a placeholder that can only come from a module yaml file. */
    static class GreetingHolder {

        @Value("${test-module.greeting}")
        String greeting;

        @Value("${test-module.only-in-base}")
        String onlyInBase;

    }

    static class KebabGreetingHolder {

        @Value("${kebab-module.greeting}")
        String greeting;

    }

    @Test
    void placeholderConfigurerIsRegistered() {

        runner
                .withUserConfiguration(SingleModule.class)
                .run(context -> assertThat(context)
                        .hasSingleBean(org.springframework.context.support.PropertySourcesPlaceholderConfigurer.class));

    }

    @Test
    void moduleYamlIsLoadedByWorkflowModuleId() {

        runner
                .withUserConfiguration(SingleModule.class)
                .withBean(GreetingHolder.class)
                .run(context -> assertThat(context.getBean(GreetingHolder.class).greeting)
                        .isEqualTo("from-module-yaml"));

    }

    @Test
    void moduleYamlIsLoadedByKebabCasedWorkflowModuleIdFromConfigDirectory() {

        runner
                .withUserConfiguration(KebabModule.class)
                .withBean(KebabGreetingHolder.class)
                .run(context -> assertThat(context.getBean(KebabGreetingHolder.class).greeting)
                        .isEqualTo("from-kebab-yaml"));

    }

    @Test
    void profileSpecificModuleYamlOverridesTheBaseYaml() {

        runner
                .withUserConfiguration(SingleModule.class)
                .withPropertyValues("spring.profiles.active=unittest")
                .withBean(GreetingHolder.class)
                .run(context -> {
                    final var holder = context.getBean(GreetingHolder.class);
                    assertThat(holder.greeting).isEqualTo("from-profile-yaml");
                    // keys not present in the profile yaml still come from the base yaml
                    assertThat(holder.onlyInBase).isEqualTo("base-value");
                });

    }

    @Test
    void yamlFilesOfAllModulesAreCombined() {

        runner
                .withUserConfiguration(TwoModules.class)
                .withBean(GreetingHolder.class)
                .withBean(KebabGreetingHolder.class)
                .run(context -> {
                    assertThat(context.getBean(GreetingHolder.class).greeting).isEqualTo("from-module-yaml");
                    assertThat(context.getBean(KebabGreetingHolder.class).greeting).isEqualTo("from-kebab-yaml");
                });

    }

    @Test
    void withoutModulesTheConfigurerIsStillRegistered() {

        runner.run(context -> assertThat(context)
                .hasSingleBean(org.springframework.context.support.PropertySourcesPlaceholderConfigurer.class));

    }

    @Test
    void aMissingModuleYamlIsNotAnError() {

        runner
                .withBean("noYaml", WorkflowModuleProperties.class,
                        () -> new WorkflowModuleProperties(WorkflowModuleIdAwareProperties.class, "moduleWithoutYaml"))
                .run(context -> assertThat(context).hasNotFailed());

    }

}
