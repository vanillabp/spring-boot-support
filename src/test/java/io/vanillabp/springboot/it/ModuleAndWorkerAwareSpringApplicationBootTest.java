package io.vanillabp.springboot.it;

import static org.assertj.core.api.Assertions.assertThat;

import io.vanillabp.springboot.ModuleAndWorkerAwareSpringApplication;
import java.util.Arrays;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Boots a real application through {@link ModuleAndWorkerAwareSpringApplication}.
 *
 * <p>This closes the gap left by {@code ModuleAndWorkerAwareSpringApplicationTest}, which invokes the
 * protected {@code configureProfiles(..)} directly: that proves the method exists and works, but not
 * that Spring Boot still calls it. Should a future Boot version stop invoking it, the direct tests stay
 * green while every application silently loses its {@code workerId} property source and its derived
 * profiles. Only a real boot detects that.
 *
 * <p>It also exercises the YAML loading path, which no other test reaches: {@code addSimulationProfiles(..)}
 * scans {@code /config/} for file names containing "simulation", parses the first top level key with
 * SnakeYAML and derives a profile name from the rest of the file name. The test resource
 * {@code config/testsim-simulation.yaml} therefore has to end up as an active profile "simulation".
 * That is the only coverage of the SnakeYAML dependency, which moved from 1.33 to 2.6 with Spring
 * Boot 4.1.
 */
class ModuleAndWorkerAwareSpringApplicationBootTest {

    private ConfigurableApplicationContext context;

    @AfterEach
    void closeContext() {

        if (context != null) {
            context.close();
            context = null;
        }
        System.clearProperty("spring.profiles.active");

    }

    private void boot(final String... args) {

        context = new ModuleAndWorkerAwareSpringApplication(TestApplication.class).run(args);

    }

    @Test
    void bootAddsTheWorkerIdPropertySourceAndItTakesPrecedence() {

        boot();

        final var environment = context.getEnvironment();

        assertThat(environment.getPropertySources().contains("workerIdProps"))
                .as("Spring Boot no longer invokes the overridden configureProfiles(..) - every "
                        + "application would silently lose its workerId")
                .isTrue();

        // application.yaml of the test resources defines workerId=from-application-yaml on purpose.
        // The property source added via addFirst(..) has to win, otherwise workers would silently pick
        // up a configured value instead of their own identity.
        //
        // Note: asserting that workerIdProps is literally the first source does not work after a full
        // boot - Spring Boot puts its own "configurationProperties" wrapper in front, which delegates
        // to the others. Resolution order is what matters, so that is what is asserted.
        assertThat(environment.getProperty("workerId")).isEqualTo("local");

    }

    @Test
    void bootActivatesTheLocalProfileWhenNoneIsGiven() {

        boot();

        assertThat(context.getEnvironment().getActiveProfiles()).contains("local");

    }

    @Test
    void bootDerivesSimulationProfilesFromYamlFilesInConfig() {

        boot();

        // requires SnakeYAML to parse config/testsim-simulation.yaml and the first top level key
        // "testsim" to be recognised as the file name prefix
        assertThat(context.getEnvironment().getActiveProfiles())
                .as("the simulation profile is derived by reading the yaml with SnakeYAML")
                .contains("simulation");

    }

    @Test
    void theSimulationYamlIsActuallyReadable() {

        boot();

        // proves SnakeYAML 2.x did not just silently skip the file: the profile is only derived when
        // the document could be parsed and its first top level key matched the file name
        assertThat(Arrays.asList(context.getEnvironment().getActiveProfiles()))
                .containsAnyOf("simulation");

    }

    @Test
    void anExplicitProfileSuppressesTheLocalDefault() {

        System.setProperty("spring.profiles.active", "unittest");
        System.setProperty(ModuleAndWorkerAwareSpringApplication.WORKER_ID_PROPERTY_NAME, "pod-boot");
        try {

            boot();

            final var profiles = context.getEnvironment().getActiveProfiles();
            assertThat(profiles).contains("unittest");
            assertThat(profiles).doesNotContain("local");
            assertThat(context.getEnvironment().getProperty("workerId")).isEqualTo("pod-boot");

        } finally {
            System.clearProperty(ModuleAndWorkerAwareSpringApplication.WORKER_ID_PROPERTY_NAME);
        }

    }

    @Test
    void theWorkflowModulePropertyPlaceholderConfigurerIsUsable() {

        boot();

        // WorkflowModulePropertiesConfiguration contributes a PropertySourcesPlaceholderConfigurer from
        // a static @Bean method taking beans as parameters - a pattern Framework 7 could reject
        assertThat(context.getBeansOfType(
                org.springframework.context.support.PropertySourcesPlaceholderConfigurer.class))
                .isNotEmpty();

    }

}
