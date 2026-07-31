package io.vanillabp.springboot.it;

import static org.assertj.core.api.Assertions.assertThat;

import io.vanillabp.spi.process.ProcessService;
import io.vanillabp.springboot.adapter.AdapterAwareProcessService;
import io.vanillabp.springboot.adapter.SpringDataUtil;
import io.vanillabp.springboot.utils.JpaSpringDataUtil;
import io.vanillabp.springboot.utils.JpaSpringDataUtilConfiguration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/**
 * The only test that exercises the real auto-configuration registration path, i.e. the file
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}. The
 * {@code ApplicationContextRunner}-based tests import the configuration classes explicitly and would
 * therefore stay green even if the registration mechanism broke.
 *
 * <p>This matters for the Spring Boot 4 migration: two of the three registered classes carry neither
 * {@code @Configuration} nor {@code @AutoConfiguration} and rely on configuration-class inference. A
 * change in the auto-configuration contract would remove their beans silently.
 */
@SpringBootTest(classes = TestApplication.class)
class AutoConfigurationRegistrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private SpringDataUtil springDataUtil;

    @Autowired
    private Map<Class<?>, AdapterAwareProcessService<?>> connectableServices;

    @Autowired
    private TestApplication.ProcessServiceConsumer processServiceConsumer;

    @Autowired
    private TestAggregateRepository testAggregateRepository;

    @Test
    void allThreeRegisteredAutoConfigurationsTookEffect() {

        // AdapterAwareProcessServiceConfiguration
        assertThat(applicationContext.containsBean("vanillaBpConnectableServices")).isTrue();
        // JpaSpringDataUtilConfiguration
        assertThat(applicationContext.containsBean(JpaSpringDataUtilConfiguration.BEANNAME_SPRINGDATAUTIL)).isTrue();
        // WorkflowModulePropertiesConfiguration
        assertThat(applicationContext
                .getBeansOfType(org.springframework.context.support.PropertySourcesPlaceholderConfigurer.class))
                .isNotEmpty();

    }

    @Test
    void jpaSpringDataUtilIsTheJpaImplementation() {

        assertThat(springDataUtil).isInstanceOf(JpaSpringDataUtil.class);

    }

    @Test
    void springDataUtilResolvesRepositoryEntityInformationAndId() {

        final var aggregate = new TestAggregate();
        aggregate.setAggregateId("a-1");
        aggregate.setPayload("payload");
        testAggregateRepository.save(aggregate);

        assertThat(springDataUtil.getRepository(TestAggregate.class)).isNotNull();
        assertThat(springDataUtil.getRepository(aggregate)).isNotNull();
        assertThat((Object) springDataUtil.getId(aggregate)).isEqualTo("a-1");
        assertThat(springDataUtil.getIdType(TestAggregate.class)).isEqualTo(String.class);
        assertThat((Object) springDataUtil.unproxy(aggregate)).isSameAs(aggregate);

    }

    @Test
    void prototypeScopedProcessServiceIsInjectedForTheAggregateType() {

        assertThat(processServiceConsumer.processService).isNotNull();
        assertThat(processServiceConsumer.processService).isInstanceOf(AdapterAwareProcessService.class);

    }

    @Test
    void connectableServicesContainsTheAggregate() {

        assertThat(connectableServices).containsKey(TestAggregate.class);

    }

    @Test
    void theProcessServiceDelegatesToTheSingleAvailableAdapter() {

        // the adapter registered its implementation for the aggregate type; the AdapterAwareProcessService
        // is the facade in front of it
        final var adapterAware = (AdapterAwareProcessService<?>) processServiceConsumer.processService;

        assertThat(connectableServices).containsValue(adapterAware);

    }

}
