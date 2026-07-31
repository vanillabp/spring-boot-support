package io.vanillabp.springboot.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.vanillabp.springboot.adapter.SpringDataUtil;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Regression net for {@link JpaSpringDataUtilConfiguration}. Written against Spring Boot 3 to document
 * the current behaviour before the Spring Boot 4 migration.
 *
 * <p>Two aspects are at risk. First, the class carries a <em>class-level</em>
 * {@code @ConditionalOnMissingBean(SpringDataUtil.class)}; class-level conditions of this kind are
 * evaluation-order sensitive. Second, the configuration uses field injection of a
 * {@code LocalContainerEntityManagerFactoryBean} - i.e. it injects a {@code FactoryBean} by its own
 * type, which is unusual and depends on how the container exposes such beans.
 *
 * <p>The functional side of {@code JpaSpringDataUtil} against a real JPA setup is covered by
 * {@code io.vanillabp.springboot.it.AutoConfigurationRegistrationTest}.
 */
class JpaSpringDataUtilConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JpaSpringDataUtilConfiguration.class));

    @Configuration
    static class OwnSpringDataUtil {

        @Bean
        SpringDataUtil myOwnSpringDataUtil() {
            return mock(SpringDataUtil.class);
        }

    }

    @Test
    void withoutJpaInfrastructureTheContextFailsHard() {

        // Documented current behaviour, and a fragility worth knowing: the class-level
        // @ConditionalOnMissingBean only guards against an *existing* SpringDataUtil. It does not make
        // the configuration conditional on JPA being present. Without an EntityManagerFactory the
        // required field injection of LocalContainerEntityManagerFactoryBean fails and takes the whole
        // context down - even for applications that only use MongoDB.
        //
        // In practice this is masked because the JPA starter is an optional dependency: applications
        // without JPA on the classpath never reach this configuration. Applications *with* the JPA
        // starter but without a configured datasource do hit it.
        runner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasMessageContaining("LocalContainerEntityManagerFactoryBean");
        });

    }

    @Test
    void anExistingSpringDataUtilBeanSuppressesTheAutoConfiguration() {

        runner
                .withUserConfiguration(OwnSpringDataUtil.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(SpringDataUtil.class);
                    assertThat(context).hasBean("myOwnSpringDataUtil");
                    assertThat(context).doesNotHaveBean(
                            JpaSpringDataUtilConfiguration.BEANNAME_SPRINGDATAUTIL);
                });

    }

    @Test
    void theBeanNameConstantIsPartOfThePublicContract() {

        // adapters reference this name via @ConditionalOnBean(name = "jpaSpringDataUtil")
        assertThat(JpaSpringDataUtilConfiguration.BEANNAME_SPRINGDATAUTIL).isEqualTo("jpaSpringDataUtil");

    }

}
