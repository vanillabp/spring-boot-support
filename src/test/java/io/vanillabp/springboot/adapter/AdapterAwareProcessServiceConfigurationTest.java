package io.vanillabp.springboot.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.vanillabp.springboot.modules.WorkflowModuleIdAwareProperties;
import io.vanillabp.springboot.modules.WorkflowModuleProperties;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.repository.CrudRepository;

/**
 * Regression net for {@link AdapterAwareProcessServiceConfiguration}. Written against Spring Boot 3 to
 * document the current behaviour before the Spring Boot 4 migration.
 *
 * <p>The class is registered as an auto-configuration but carries neither {@code @Configuration} nor
 * {@code @AutoConfiguration}; it relies on configuration-class inference. Its
 * {@code @PostConstruct} method performs cross-bean validation and mutates
 * {@link VanillaBpProperties} - behaviour that has to survive the migration unchanged, because it
 * decides which adapter a workflow is wired to.
 */
class AdapterAwareProcessServiceConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class,
                    AdapterAwareProcessServiceConfiguration.class))
            .withPropertyValues("spring.application.name=test-app");

    @SuppressWarnings({ "rawtypes", "unchecked" })
    static class FakeAdapterConfiguration extends AdapterConfigurationBase<ProcessServiceImplementation<Object>> {

        private final String adapterId;

        FakeAdapterConfiguration(final String adapterId) {
            this.adapterId = adapterId;
        }

        @Override
        public String getAdapterId() {
            return adapterId;
        }

        @Override
        public <DE> ProcessServiceImplementation<Object> newProcessServiceImplementation(
                final SpringDataUtil springDataUtil,
                final Class<DE> workflowAggregateClass,
                final Class<?> workflowAggregateIdClass,
                final CrudRepository<DE, Object> workflowAggregateRepository) {

            final ProcessServiceImplementation implementation = mock(ProcessServiceImplementation.class);
            when(implementation.getWorkflowAggregateClass()).thenReturn(workflowAggregateClass);
            return implementation;

        }

    }

    @Configuration
    static class OneAdapter {

        @Bean
        AdapterConfigurationBase<?> camunda7() {
            return new FakeAdapterConfiguration("camunda7");
        }

    }

    @Configuration
    static class TwoAdapters {

        @Bean
        AdapterConfigurationBase<?> camunda7() {
            return new FakeAdapterConfiguration("camunda7");
        }

        @Bean
        AdapterConfigurationBase<?> camunda8() {
            return new FakeAdapterConfiguration("camunda8");
        }

    }

    @Configuration
    static class ExplicitWorkflowModule {

        @Bean
        WorkflowModuleProperties loanApproval() {
            return new WorkflowModuleProperties(WorkflowModuleIdAwareProperties.class, "loan-approval");
        }

    }

    @Test
    void withoutAnyAdapterTheContextFailsFast() {

        runner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasRootCauseMessage("No VanillaBP adapter was found in classpath!");
        });

    }

    /**
     * Every adapter of every workflow module needs a {@code resources-location}. Without it, validation
     * fails - see {@link #aMissingResourcesLocationFailsFast()}.
     */
    private static final String C7_RESOURCES =
            "vanillabp.workflow-modules.test-app.adapters.camunda7.resources-location=classpath*:/wr/c7";

    private static final String C8_RESOURCES =
            "vanillabp.workflow-modules.test-app.adapters.camunda8.resources-location=classpath*:/wr/c8";

    @Test
    void withOneAdapterThatAdapterBecomesTheDefault() {

        runner
                .withUserConfiguration(OneAdapter.class)
                .withPropertyValues(C7_RESOURCES)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    // side effect of the @PostConstruct validation
                    assertThat(context.getBean(VanillaBpProperties.class).getDefaultAdapter())
                            .containsExactly("camunda7");
                });

    }

    @Test
    void aMissingResourcesLocationFailsFast() {

        // Documented current behaviour: the @PostConstruct validation insists on an adapter-specific
        // resources-location for the implicit workflow module named after spring.application.name.
        runner
                .withUserConfiguration(OneAdapter.class)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("Property 'vanillabp.workflow-modules.test-app.adapters"
                                    + ".camunda7.resources-location' not set!\nIt has to point to a location "
                                    + "specific to the adapter in order to avoid future problems once you wish "
                                    + "to migrate to another adapter.\nSample: 'classpath*:/workflow-resources/"
                                    + "camunda7'");
                });

    }

    @Test
    void withTwoAdaptersNoDefaultIsDerivedAndTheContextStillStarts() {

        runner
                .withUserConfiguration(TwoAdapters.class)
                .withPropertyValues(C7_RESOURCES, C8_RESOURCES)
                .run(context -> {
                    // Documented current behaviour: with two adapters no default can be derived, but
                    // startup succeeds. The "no default adapter is configured" error is raised later, by
                    // validatePropertiesFor(..), when a concrete workflow is wired. So an application
                    // with an ambiguous adapter setup boots fine and only fails once a workflow is used.
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(VanillaBpProperties.class).getDefaultAdapter()).isEmpty();
                });

    }

    @Test
    void wiringAWorkflowWithoutAConfiguredDefaultAdapterFailsWithAHelpfulMessage() {

        runner
                .withUserConfiguration(TwoAdapters.class)
                .withPropertyValues(C7_RESOURCES, C8_RESOURCES)
                .run(context -> {
                    final var properties = context.getBean(VanillaBpProperties.class);

                    org.assertj.core.api.Assertions
                            .assertThatThrownBy(() -> properties.validatePropertiesFor(
                                    java.util.List.of("camunda7", "camunda8"), "test-app", "SomeProcess"))
                            .isInstanceOf(RuntimeException.class)
                            .hasMessageContaining("no default adapter is configured")
                            .hasMessageContaining("vanillabp.default-adapter");
                });

    }

    @Test
    void withTwoAdaptersAnExplicitlyConfiguredDefaultIsKept() {

        runner
                .withUserConfiguration(TwoAdapters.class)
                .withPropertyValues(C7_RESOURCES, C8_RESOURCES, "vanillabp.default-adapter=camunda8")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(VanillaBpProperties.class).getDefaultAdapter())
                            .containsExactly("camunda8");
                });

    }

    @Test
    void theConnectableServicesMapIsExposedAsABean() {

        runner
                .withUserConfiguration(OneAdapter.class)
                .withPropertyValues(C7_RESOURCES)
                .run(context -> {
                    assertThat(context).hasBean("vanillaBpConnectableServices");
                    assertThat(context.getBean("vanillaBpConnectableServices")).isInstanceOf(Map.class);
                });

    }

    @Test
    void withoutExplicitWorkflowModulesTheApplicationNameIsUsedAsModuleId() {

        // configuration for a module named like the application must be accepted ...
        runner
                .withUserConfiguration(OneAdapter.class)
                .withPropertyValues(
                        "vanillabp.workflow-modules.test-app.adapters.camunda7.resources-location=classpath*:/a")
                .run(context -> assertThat(context).hasNotFailed());

    }

    @Test
    void explicitlyDeclaredWorkflowModulesAreUsedForValidation() {

        runner
                .withUserConfiguration(OneAdapter.class, ExplicitWorkflowModule.class)
                .withPropertyValues(
                        "vanillabp.workflow-modules.loan-approval.adapters.camunda7.resources-location=classpath*:/a")
                .run(context -> assertThat(context).hasNotFailed());

    }

}
