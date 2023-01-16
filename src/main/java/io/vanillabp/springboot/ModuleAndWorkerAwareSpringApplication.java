package io.vanillabp.springboot;

import io.vanillabp.springboot.utils.ClasspathScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

public class ModuleAndWorkerAwareSpringApplication extends SpringApplication {

    public static final String WORKER_ID_ENV_NAME = "WORKER_ID";

    public static final String WORKER_ID_PROPERTY_NAME = "workerId";

    private final Logger logger;

    public ModuleAndWorkerAwareSpringApplication(
            final Class<?> application) {
        
        super(application);
        
        logger = LoggerFactory.getLogger(application);

    }
    
    @Override
    public ConfigurableApplicationContext run(
            final String... args) {
        
        var context = super.run(args);

        final var environment = context.getEnvironment();
        logger.info("Application booted successfully. Active profiles are {}.",
                Arrays.toString(environment.getActiveProfiles()));
        
        return context;
    }
    
    @Override
    protected void configureProfiles(
            final ConfigurableEnvironment environment,
            final String[] args) {
        
        final var activeProfilesSystemProperty = System.getProperty("spring.profiles.active");

        // if no profiles configured the add profile 'local' which means the
        // developer's local runtime environment used to develop and test the application
        if ((environment.getActiveProfiles().length == 0)
                && !StringUtils.hasText(activeProfilesSystemProperty)) {
            
            // as part of the blueprint building simulation-services
            // to simulate bounded systems for local development or integration
            // testing is recommended. Therefore the profiles specific to the
            // simulation-environment are activated by default for local development.
            addSimulationProfiles(environment);

            // set development profile 'local'
            environment.addActiveProfile("local");
            
        }

        final var activeProfiles = retrieveAndUpdateActiveProfiles(activeProfilesSystemProperty, environment);

        // add worker-id property
        environment
                .getPropertySources()
                .addFirst(getWorkerIdProperties(activeProfiles));

        // add recursively "sub-profiles" based on the given profiles
        activateParentProfiles(environment, activeProfiles);
        
    }
    
    /**
     * Before starting Spring Boot application one has to determine the active
     * profiles without support of Spring Boot.
     */
    private static Set<String> retrieveAndUpdateActiveProfiles(
            final String activeProfilesSystemProperty,
            final ConfigurableEnvironment environment) {
        
        final var result = new LinkedHashSet<String>();
        
        Arrays.stream(environment.getActiveProfiles())
                .forEach(result::add);
        
        if (StringUtils.hasText(activeProfilesSystemProperty)) {
        
            Arrays.stream(activeProfilesSystemProperty.split(","))
                    .map(String::trim)
                    .forEach(result::add);
            
        }
        
        environment.setActiveProfiles(result.toArray(String[]::new));
        
        return result;
        
    }

    /**
     * Build property source for injection of the worker-id value in Spring beans.
     */
    private static PropertiesPropertySource getWorkerIdProperties(
            final Collection<String> activeProfiles) {

        final var props = new Properties();
        props.setProperty(WORKER_ID_PROPERTY_NAME, getWorkerId(activeProfiles));
        return new PropertiesPropertySource("workerIdProps", props);

    }

    /**
     * Fetch the worker-id from the environment. In Kubernetes environments this is
     * typically the pod's name. In case of local development environment and
     * missing environment variable the id 'local' is used.
     */
    private static String getWorkerId(
            final Collection<String> activeProfiles) {

        var workerId = System.getenv(WORKER_ID_ENV_NAME);
        if (workerId == null) {
            workerId = System.getProperty(WORKER_ID_ENV_NAME);
        }

        if (workerId == null) {
            var isDevelopment = activeProfiles.stream().anyMatch(profile -> profile.matches("local"));
            if (isDevelopment) {
                workerId = "local";
            }
        }

        if (workerId == null) {
            throw new RuntimeException("No environment variable '" + WORKER_ID_ENV_NAME
                    + "' given! This is necessary to run in clustered environments properly.");
        }

        return workerId;

    }

    /**
     * Fetch all yaml files in classpath '/config/**' and extract their profiles if
     * they belong to simulation.
     */
    private static void addSimulationProfiles(
            final ConfigurableEnvironment environment) {
        
        final List<Resource> yamlFiles;
        try {
            // fetch all *.yaml files
            yamlFiles = ClasspathScanner
                    .allResources("/config/",
                            r -> r.getFilename().endsWith(".yaml") || r.getFilename().endsWith(".yml"));
        
            yamlFiles
                    .stream()
                    .filter(r -> r.getFilename().contains("simulation"))
                    .map(r -> {
                        // fetch the yaml's first top level item
                        final var moduleName = getModuleName(r);
                        if (moduleName == null) {
                            return null;
                        }
                        // assume filename starts with module name to extract the profile's name
                        if (!r.getFilename().startsWith(moduleName)) {
                            throw new RuntimeException(
                                    "It is necessary to use the yaml's first top level item '"
                                    + moduleName
                                    + "' as prefix for the filename of '"
                                    + r
                                    + "'! E.g. '"
                                    + moduleName
                                    + "-simulation.y[a]ml'");
                        }
                        // map to profile name
                        return r
                                .getFilename()
                                .substring(moduleName.length() + 1,
                                        r.getFilename().lastIndexOf('.'));
                    })
                    .filter(profile -> profile != null)
                    .distinct()
                    .forEach(profile -> environment.addActiveProfile(profile));

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    private static void activateParentProfiles(
            final ConfigurableEnvironment environment,
            final Collection<String> profiles) {

        final var profilesToBeProcessed = List.copyOf(profiles); // avoid ConcurrentModificationException
        for (final var profile : profilesToBeProcessed) {
            activateParentProfiles(environment, profiles, profile);
        }

    }

    /**
     * This is used to build hierarchically parent-profiles derived from the given
     * leave profile.
     * <p>
     * <ul>
     * <li>Given: x-y-z</li>
     * <li>Derived: x-y</li>
     * <li>Derived: x</li>
     * </ul>
     */
    private static void activateParentProfiles(
            final ConfigurableEnvironment environment,
            final Collection<String> preConfiguredProfiles,
            final String profile) {

        var baseProfile = profile;
        final var fractions = profile.split("-");
        for (int i = fractions.length - 1; i >= 0; --i) {
            final var name = getParentProfileName(fractions, i);
            if (!preConfiguredProfiles.contains(name)) {
                addNewParentProfileInFrontOfBaseProfile(environment, baseProfile, name);
                baseProfile = name;
                preConfiguredProfiles.add(name);
            }
        }

    }
    
    private static String getParentProfileName(
            final String[] fractions,
            final int no) {
        
        final var profileName = new StringBuffer();
        for (int i = 0; i <= no; ++i) {
            if (i > 0) {
                profileName.append('-');
            }
            profileName.append(fractions[i]);
        }
        return profileName.toString();
        
    }

    private static void addNewParentProfileInFrontOfBaseProfile(
            final ConfigurableEnvironment environment,
            final String baseProfile,
            final String newProfile) {
        
        final var newProfiles = new LinkedList<String>();
        
        Arrays.stream(environment.getActiveProfiles())
                .forEach(profile -> {
                    if (profile.equals(baseProfile)) {
                        newProfiles.add(newProfile);
                    }
                    newProfiles.add(profile);
                });
        
        environment.setActiveProfiles(
                newProfiles.toArray(String[]::new));
        
    }

    /**
     * @return The 'module name' which is the first top-level scope.
     */
    private static String getModuleName(final Resource resource) {

        try {

            final var loaderOptions = new LoaderOptions();
            loaderOptions.setAllowDuplicateKeys(false);
            final var yaml = new Yaml(loaderOptions);

            try (final var yamlStream = resource.getInputStream()) {

                Map<String, Object> content = yaml.load(yamlStream);
                if (content == null) {
                    return null;
                }

                return content
                        .entrySet()
                        .stream()
                        .filter(entry -> resource.getFilename().startsWith(entry.getKey()))
                        .findFirst()
                        .map(Entry::getKey)
                        .orElse(null);

            }

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

}
