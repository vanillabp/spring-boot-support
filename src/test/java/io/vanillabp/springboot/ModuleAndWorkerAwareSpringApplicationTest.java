package io.vanillabp.springboot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;

/**
 * Regression net for {@link ModuleAndWorkerAwareSpringApplication}. Written against Spring Boot 3 to
 * document the current behaviour before the Spring Boot 4 migration.
 *
 * <p>This class overrides {@code SpringApplication#configureProfiles(..)}, a protected SPI. If that
 * method is removed the build breaks loudly; if it is merely no longer invoked by Spring Boot, the
 * failure is silent: the application starts, but the {@code workerId} property source is missing and
 * the derived profiles are absent. The tests below therefore assert on both outcomes of the method -
 * the resulting profile set and the injected property source.
 *
 * <p>The test lives in the same package as the class under test so the protected method can be
 * invoked directly, without booting an application context.
 */
class ModuleAndWorkerAwareSpringApplicationTest {

    private static final String WORKER_ID = ModuleAndWorkerAwareSpringApplication.WORKER_ID_PROPERTY_NAME;

    private static final String WORKER_ID_ENV = ModuleAndWorkerAwareSpringApplication.WORKER_ID_ENV_NAME;

    /** Exposes the protected method for testing. */
    static class TestableApplication extends ModuleAndWorkerAwareSpringApplication {

        TestableApplication() {
            super(TestableApplication.class);
        }

        void callConfigureProfiles(final ConfigurableEnvironment environment) {
            configureProfiles(environment, new String[0]);
        }

    }

    @AfterEach
    void clearSystemProperties() {

        System.clearProperty("spring.profiles.active");
        System.clearProperty(WORKER_ID);
        System.clearProperty(WORKER_ID_ENV);

    }

    @Test
    void withoutAnyProfileTheLocalProfileIsActivated() {

        final var environment = new StandardEnvironment();

        new TestableApplication().callConfigureProfiles(environment);

        assertThat(environment.getActiveProfiles()).contains("local");

    }

    @Test
    void withoutAnyProfileTheWorkerIdDefaultsToLocal() {

        final var environment = new StandardEnvironment();

        new TestableApplication().callConfigureProfiles(environment);

        assertThat(environment.getProperty(WORKER_ID)).isEqualTo("local");

    }

    @Test
    void workerIdPropertySourceIsAddedFirst() {

        final var environment = new StandardEnvironment();

        new TestableApplication().callConfigureProfiles(environment);

        // precedence matters: the property source must win over anything else providing 'workerId'
        assertThat(environment.getPropertySources().iterator().next().getName())
                .isEqualTo("workerIdProps");

    }

    @Test
    void workerIdIsTakenFromSystemProperty() {

        System.setProperty(WORKER_ID, "pod-4711");
        final var environment = new StandardEnvironment();

        new TestableApplication().callConfigureProfiles(environment);

        assertThat(environment.getProperty(WORKER_ID)).isEqualTo("pod-4711");

    }

    @Test
    void explicitProfileSuppressesTheLocalDefault() {

        System.setProperty("spring.profiles.active", "camunda7");
        System.setProperty(WORKER_ID, "pod-1");
        final var environment = new StandardEnvironment();

        new TestableApplication().callConfigureProfiles(environment);

        assertThat(environment.getActiveProfiles()).contains("camunda7");
        assertThat(environment.getActiveProfiles()).doesNotContain("local");

    }

    @Test
    void withoutLocalProfileAndWithoutWorkerIdTheStartupFails() {

        System.setProperty("spring.profiles.active", "production");
        final var environment = new StandardEnvironment();

        // this is intentional fail-fast behaviour: a clustered runtime must provide a worker id
        assertThatThrownBy(() -> new TestableApplication().callConfigureProfiles(environment))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining(WORKER_ID_ENV);

    }

    @Test
    void parentProfilesAreDerivedFromHierarchicalProfileNames() {

        System.setProperty("spring.profiles.active", "aws-test-eu");
        System.setProperty(WORKER_ID, "pod-2");
        final var environment = new StandardEnvironment();

        new TestableApplication().callConfigureProfiles(environment);

        // 'aws-test-eu' implies 'aws-test' and 'aws'
        assertThat(environment.getActiveProfiles())
                .contains("aws", "aws-test", "aws-test-eu");

    }

    @Test
    void parentProfilesArePlacedBeforeTheirLeafProfile() {

        System.setProperty("spring.profiles.active", "aws-test");
        System.setProperty(WORKER_ID, "pod-3");
        final var environment = new StandardEnvironment();

        new TestableApplication().callConfigureProfiles(environment);

        final var profiles = Arrays.asList(environment.getActiveProfiles());
        assertThat(profiles.indexOf("aws")).isLessThan(profiles.indexOf("aws-test"));

    }

    @Test
    void multipleExplicitProfilesAreAllActivated() {

        System.setProperty("spring.profiles.active", "camunda7, cockpit");
        System.setProperty(WORKER_ID, "pod-5");
        final var environment = new StandardEnvironment();

        new TestableApplication().callConfigureProfiles(environment);

        assertThat(environment.getActiveProfiles()).contains("camunda7", "cockpit");

    }

}
