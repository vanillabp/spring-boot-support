package io.vanillabp.springboot.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * Regression net for the binding of {@link VanillaBpProperties}. Written against Spring Boot 3 to
 * document the current behaviour before the Spring Boot 4 migration.
 *
 * <p>The critical aspect is not that values arrive, but that the side effects of the setters
 * {@link VanillaBpProperties#setWorkflowModules(java.util.Map)} and
 * {@link VanillaBpProperties.WorkflowModuleAdapterProperties#setWorkflows(java.util.Map)} are
 * applied: they back-link every nested object to its parent and inject the map key as id. If a
 * future binder implementation stops calling those setters, or calls them with an incomplete map,
 * the configuration silently degrades - no exception, but empty ids and null parents.
 */
class VanillaBpPropertiesBindingTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(TestConfiguration.class);

    @Configuration
    @EnableConfigurationProperties(VanillaBpProperties.class)
    static class TestConfiguration {
    }

    @Test
    void bindsDefaultAdapterOnTopLevel() {

        runner
                .withPropertyValues("vanillabp.default-adapter=camunda7")
                .run(context -> assertThat(context.getBean(VanillaBpProperties.class).getDefaultAdapter())
                        .containsExactly("camunda7"));

    }

    @Test
    void emptyConfigurationYieldsEmptyCollections() {

        runner.run(context -> {
            final var properties = context.getBean(VanillaBpProperties.class);
            assertThat(properties.getDefaultAdapter()).isEmpty();
            assertThat(properties.getWorkflowModules()).isEmpty();
        });

    }

    @Test
    void workflowModuleKeyIsInjectedAsWorkflowModuleId() {

        runner
                .withPropertyValues(
                        "vanillabp.workflow-modules.loan-approval.adapters.camunda7.resources-location=classpath*:/wr/c7")
                .run(context -> {
                    final var properties = context.getBean(VanillaBpProperties.class);
                    assertThat(properties.getWorkflowModules()).containsOnlyKeys("loan-approval");
                    final var module = properties.getWorkflowModules().get("loan-approval");
                    // side effect of setWorkflowModules(..)
                    assertThat(module.getWorkflowModuleId()).isEqualTo("loan-approval");
                    assertThat(module.getDefaultProperties()).isSameAs(properties);
                });

    }

    @Test
    void workflowKeyIsInjectedAsBpmnProcessIdAndWorkflowModuleIsBackLinked() {

        runner
                .withPropertyValues(
                        "vanillabp.workflow-modules.loan-approval.workflows.LoanApproval.default-adapter=camunda8")
                .run(context -> {
                    final var properties = context.getBean(VanillaBpProperties.class);
                    final var module = properties.getWorkflowModules().get("loan-approval");
                    assertThat(module.getWorkflows()).containsOnlyKeys("LoanApproval");
                    final var workflow = module.getWorkflows().get("LoanApproval");
                    // side effect of setWorkflows(..)
                    assertThat(workflow.getBpmnProcessId()).isEqualTo("LoanApproval");
                    assertThat(workflow.getWorkflowModule()).isSameAs(module);
                    assertThat(workflow.getDefaultAdapter()).containsExactly("camunda8");
                });

    }

    @Test
    void multipleWorkflowModulesAreAllBackLinked() {

        runner
                .withPropertyValues(
                        "vanillabp.workflow-modules.first.adapters.camunda7.resources-location=classpath*:/a",
                        "vanillabp.workflow-modules.second.adapters.camunda8.resources-location=classpath*:/b",
                        "vanillabp.workflow-modules.second.workflows.Second.default-adapter=camunda8")
                .run(context -> {
                    final var properties = context.getBean(VanillaBpProperties.class);
                    assertThat(properties.getWorkflowModules()).containsOnlyKeys("first", "second");
                    properties.getWorkflowModules().forEach((id, module) -> {
                        assertThat(module.getWorkflowModuleId()).isEqualTo(id);
                        assertThat(module.getDefaultProperties()).isSameAs(properties);
                        module.getWorkflows().forEach((bpmnProcessId, workflow) -> {
                            assertThat(workflow.getBpmnProcessId()).isEqualTo(bpmnProcessId);
                            assertThat(workflow.getWorkflowModule()).isSameAs(module);
                        });
                    });
                });

    }

    @Test
    void unknownFieldsAreIgnored() {

        // the properties class is annotated ignoreUnknownFields = true, which is what allows the
        // adapters to bind additional beans to the same 'vanillabp' prefix
        runner
                .withPropertyValues("vanillabp.this-property-does-not-exist=whatever")
                .run(context -> assertThat(context).hasSingleBean(VanillaBpProperties.class));

    }

    @Test
    void adapterResourcesLocationIsResolvedPerModuleAndAdapter() {

        runner
                .withPropertyValues(
                        "vanillabp.workflow-modules.loan-approval.adapters.camunda7.resources-location=classpath*:/workflow-resources/c7")
                .run(context -> {
                    final var properties = context.getBean(VanillaBpProperties.class);
                    assertThat(properties.getAdapterResourcesLocationFor("loan-approval", "camunda7"))
                            .isEqualTo("classpath*:/workflow-resources/c7");
                });

    }

    @Test
    void defaultAdapterFallsBackFromWorkflowToModuleToGlobal() {

        runner
                .withPropertyValues(
                        "vanillabp.default-adapter=camunda7",
                        "vanillabp.workflow-modules.overridden.default-adapter=camunda8")
                .run(context -> {
                    final var properties = context.getBean(VanillaBpProperties.class);
                    assertThat(properties.getDefaultAdapterFor("unknown-module", null))
                            .containsExactly("camunda7");
                    assertThat(properties.getDefaultAdapterFor("overridden", null))
                            .containsExactly("camunda8");
                });

    }

    @Test
    void validatePropertiesAcceptsAKnownConfiguration() {

        runner
                .withPropertyValues(
                        "vanillabp.default-adapter=camunda7",
                        "vanillabp.workflow-modules.loan-approval.adapters.camunda7.resources-location=classpath*:/a")
                .run(context -> {
                    final var properties = context.getBean(VanillaBpProperties.class);
                    assertThatNoException().isThrownBy(
                            () -> properties.validateProperties(
                                    java.util.List.of("camunda7"),
                                    java.util.List.of("loan-approval")));
                });

    }

}
