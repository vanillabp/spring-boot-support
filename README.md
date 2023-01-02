![Draft](./readme/vanillabp-headline.png)
# Spring Boot Support

On using Spring Boot for building business processing applications, a couple of patterns can be identified. This module consists of implementations of those patterns as well as classes used by Spring Boot based [VanillaBP](https://www.github.com/vanillabp/spi-for-java) adapters.


To enable these features one has to use the Spring application class `ModuleAndWorkerAwareSpringApplication` instead of `SpringApplication` in the applications main-method:

```java
import io.vanillabp.springboot.ModuleAndWorkerAwareSpringApplication;

@SpringBootApplication
@ComponentScan(basePackageClasses = { TaxiApplication.class })
public class TaxiApplication {
    public static void main(String... args) {
        new ModuleAndWorkerAwareSpringApplication(TaxiApplication.class).run(args);
    }
}
```

## Content

1. [Worker ID](#worker-id)
1. [Workflow modules](#workflow-modules)
1. [Spring boot profiles](#spring-boot-profiles)
1. [Noteworthy & Contributors](#noteworthy--contributors)
1. [License](#license)

## Worker ID

In a decentralized environment workload is fetched rather then pushed to deal with back-pressure (e.g. Camunda 7's external tasks or Camunda 8's workers). In a load-balanced cluster environment, to identify each particular node fetching jobs from an external service in a unique way, the client has to pass a worker ID to that service. This worker ID is typically fetched from the environment like the host's IP address or a Kubernetes pod's name.

To read this ID from the environment and pass it to the client bean is a feature of the Spring application class `ModuleAndWorkerAwareSpringApplication`. First the system environment `WORKER_ID` is read and if empty then the Java system property `WORKER_ID` is used.

So, one can start the Java process like this: 

```sh
java -DWORKER_ID=$(hostname -i) -jar taxi-application.jar
```

Or, one can map a Kubernetes pod's name in the Kubernetes deployment.yaml file:

```yaml
spec:
  ...
  template:
    ...
    spec:
      containers:
      - name: taxi-application
        env:
          - name: "WORKER_ID"
            valueFrom:
              fieldRef:
                fieldPath: metadata.name
```

Finally, it can be used in a Spring bean by injection:

```java
@Value("${workerId}")
private String workerId;
```

Currently this value is used by BPMS adapters.

## Workflow modules

For each use-case to be implemented, the BPMN and the underlying implementation form a unit called workflow module. This bundle is meant to be deployed together. An example would be different workflows of a taxi-ride like the main workflow and secondary workflows as changing or cancelling the taxi-ride.

To encapsulate a workflow module in its runtime environment (e.g. Spring boot container, JEE application container) all necessary configuration and dependencies are bundled as if it were a standalone application. Therefore, each workflow should be an independent module (jar) containing classes and resources (e.g. the BPMN file).

### Configuration

In a Spring boot environment one can use [externalized properties](https://www.baeldung.com/spring-yaml) to store configuration details. Typically a YAML formatted file stored in classpath `config/application.yaml` is used. In a workflow module the same mechanism is used, but the name of the YAML file is customized (e.g. `ride.yaml` for the taxi ride example).

To simplify configuration the file's name as well as the configuration's top section is the name of the workflow module. Therefore the name is typically formatted in kebap case. This name is called *workflow module ID* and is also used to separate the modules within the underlying BPMS.

`config/ride.yaml`:

```yaml
ride:
  ...
```

To mark a properties class as workflow module properties a special bean `WorkflowModuleProperties` has to be produced which used to make Spring Boot aware of those additional configuration files. Additionally, the interface `WorkflowModuleIdAwareProperties` has to be implemented which is used by BPMS adapters:

```java
@Configuration
@ConfigurationProperties(prefix = RideProperties.WORKFLOW_MODULE_ID)
public class RideProperties
        implements WorkflowModuleIdAwareProperties {
    public static final String WORKFLOW_MODULE_ID = "ride";

    @Bean
    public static WorkflowModuleProperties rideProps() {
        return new WorkflowModuleProperties(RideProperties.class, WORKFLOW_MODULE_ID);
    }
    ...
}
```

## Spring boot profiles

Typically profiles are used to set environment specific properties (e.g. stages):

1. config/ride.yaml (stores the default values)
1. config/ride-local.yaml (configuration specific to the local development environment)
1. config/ride-test.yaml (configuration specific to the test environment)
1. config/ride-production.yaml (configuration specific to the production environment)

or to establish feature switches:

1. config/ride-camunda7.yaml (configuration used by Camunda 7 adapter)
1. config/ride-camunda8.yaml (configuration used by Camunda 8 adapter)

To choose a specific profile the system property `spring.profiles.active` has to be set (e.g `-Dspring.profiles.active=camunda7,test` enables Camunda 7 adapter in the test environment).

### Hierarchical profiles

If you provide several stage environments then some of the configuration properties might be the same. You can put them into the main YAML file (e.g. `config/ride.yaml`).

Imagine you want to have more then one test environment because you need to test a bugfix release in parallel to the test team which is testing the next major release of your software. In this situation you might have multiple, enumerated environments like `test-env1` and `test-env2`. Typically, most of the configuration is the same for both test environments but you have to copy them into both YAML files `config/ride-test-env1.yaml` and `config/ride-test-env2.yaml` since they cannot be put into the major YAML file which must not contain any environment specific configuration.

To overcome this, Spring boot's default mechanism of profile determination is extended to automatically add base profiles according to the kebap case: `-Dspring.profiles.active=test-env1` will be interpreted as `-Dspring.profiles.active=test,test-env1` and therefore the file `config/ride-test.yaml` and `config/ride-test-env1.yaml` are read next to `config/ride.yaml`. One starting the application you will see a log-line showing the actual profiles used e.g.

```
INFO ..... The following profiles are active: camunda7,test,test-env1
```

This hierarchically mechanisms can be used in combination with the `application.yaml` and expressions to deduplicate configuration even across multiple workflow modules. In this example the same driver API is used by two independent workflow modules hosted by the same Spring Boot application:

application-test.yaml:

```yaml
endpoints:
  driver-api:
     base-uri: https://test.driver-service.com/api
```

ride.yaml:

```yaml
ride:
  driver-api-client:
     base-uri: ${endpoints.driver-api.base-uri}
```

payment.yaml:

```yaml
payment:
  driver-api-client:
     base-uri: ${endpoints.driver-api.base-uri}
```

### Special profiles

There are two profiles "local" and "simulation" which a treaded in a special way:

*local* is the profile used for local development in your IDE. If no profile is defined at all then this profile is selected as a default. Additionally, the worker ID is set to `local` if non is set.

*simulation* is the feature-switch profile to use simulated external systems instead of accessing real APIs. It is activated per default next to the *local* profile, if no profile is defined. A "simulation" is an additional Spring Boot container you can build which implements the interfaces of all external systems (e.g. REST-APIs, embedded LDAP instead of ActiveDirectory, embedded Kafka, etc.) to be used for local development as well as for running integration tests as part of the build.

## Noteworthy & Contributors

[VanillaBP](https://www.github.com/vanillabp/spi-for-java) was developed by [Phactum](https://www.phactum.at) with the intention of giving back to the community as it has benefited the community in the past.

![Phactum](./readme/phactum.png)

## License

Copyright 2022 Phactum Softwareentwicklung GmbH

Licensed under the Apache License, Version 2.0
