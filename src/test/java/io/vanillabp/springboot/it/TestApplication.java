package io.vanillabp.springboot.it;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.vanillabp.spi.process.ProcessService;
import io.vanillabp.springboot.adapter.AdapterAwareProcessService;
import io.vanillabp.springboot.adapter.AdapterConfigurationBase;
import io.vanillabp.springboot.adapter.ProcessServiceImplementation;
import io.vanillabp.springboot.adapter.SpringDataUtil;
import java.util.Map;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.data.mongodb.autoconfigure.DataMongoAutoConfiguration;
import org.springframework.boot.data.mongodb.autoconfigure.DataMongoReactiveAutoConfiguration;
import org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration;
import org.springframework.boot.mongodb.autoconfigure.MongoReactiveAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.repository.CrudRepository;

/**
 * Minimal application used by the integration tests. It provides exactly what
 * {@code AdapterAwareProcessServiceConfiguration} requires - one adapter configuration - and,
 * through the JPA starter plus H2, the Spring Data infrastructure that
 * {@code JpaSpringDataUtilConfiguration} needs.
 *
 * <p>The process-service implementation itself is a Mockito mock: only the wiring is under test, and
 * a mock keeps this test independent of future additions to the {@code ProcessService} interface.
 */
/*
 * MongoDB auto-configuration is excluded because the optional MongoDB starter is on the test
 * classpath and would try to reach a server that is not running. The exclusions reference the classes
 * directly on purpose: should Spring Boot relocate them, this becomes a compile error instead of a
 * silent behaviour change.
 */
@SpringBootApplication(exclude = {
        MongoAutoConfiguration.class,
        MongoReactiveAutoConfiguration.class,
        DataMongoAutoConfiguration.class,
        DataMongoReactiveAutoConfiguration.class })
public class TestApplication {

    public static final String ADAPTER_ID = "test-adapter";

    @Bean
    AdapterConfigurationBase<?> testAdapterConfiguration() {
        return new TestAdapterConfiguration();
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static class TestAdapterConfiguration
            extends AdapterConfigurationBase<ProcessServiceImplementation<Object>> {

        @Override
        public String getAdapterId() {
            return ADAPTER_ID;
        }

        @Override
        public <DE> ProcessServiceImplementation<Object> newProcessServiceImplementation(
                final SpringDataUtil springDataUtil,
                final Class<DE> workflowAggregateClass,
                final Class<?> workflowAggregateIdClass,
                final CrudRepository<DE, Object> workflowAggregateRepository) {

            final ProcessServiceImplementation implementation = mock(ProcessServiceImplementation.class);
            when(implementation.getWorkflowAggregateClass()).thenReturn(workflowAggregateClass);
            when(implementation.getWorkflowAggregateRepository()).thenReturn(workflowAggregateRepository);
            when(implementation.getPrimaryBpmnProcessId()).thenReturn("TestProcess");
            putConnectableService(workflowAggregateClass, implementation);
            return implementation;

        }

    }

    /**
     * Consumer of a prototype-scoped {@link ProcessService}. Exercises the {@code InjectionPoint} plus
     * {@code ParameterizedType} logic of {@code AdapterAwareProcessServiceConfiguration}.
     */
    @Bean
    ProcessServiceConsumer processServiceConsumer(
            final ProcessService<TestAggregate> processService,
            final Map<Class<?>, AdapterAwareProcessService<?>> connectableServices) {

        return new ProcessServiceConsumer(processService, connectableServices);

    }

    public static class ProcessServiceConsumer {

        public final ProcessService<TestAggregate> processService;

        public final Map<Class<?>, AdapterAwareProcessService<?>> connectableServices;

        ProcessServiceConsumer(
                final ProcessService<TestAggregate> processService,
                final Map<Class<?>, AdapterAwareProcessService<?>> connectableServices) {

            this.processService = processService;
            this.connectableServices = connectableServices;

        }

    }

}
