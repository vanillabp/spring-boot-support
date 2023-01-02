package io.vanillabp.springboot.utils;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.util.ClassUtils;
import org.springframework.util.SystemPropertyUtils;

public class ClasspathScanner {

    private static Logger logger = LoggerFactory.getLogger(ClasspathScanner.class);

    private static final Map<String, Resource[]> cache = new HashMap<>();

    private ClasspathScanner() {
        // static class: hide public constructor
    }
    
    private static ResourcePatternResolver getResourcePatternResolver(
    		final ResourceLoader resourceLoader) {
    	if (resourceLoader == null) {
    		return new PathMatchingResourcePatternResolver();
    	} else {
    		return new PathMatchingResourcePatternResolver(resourceLoader);
    	}
    }
    
    @SafeVarargs
    public static List<Resource> allResources(
    		final Predicate<Resource>... filters) throws Exception {
        
        return allResources(null, null, filters);
        
    }

    @SafeVarargs
    public static List<Resource> allResources(
    		final ResourceLoader resourceLoader,
    		final Predicate<Resource>... filters) throws Exception {
        
        return allResources(resourceLoader, null, filters);
        
    }

    @SafeVarargs
    public static List<Resource> allResources(
    		final String basePath,
    		final Predicate<Resource>... filters) throws Exception {
    	
    	return allResources(null, basePath, filters);
    	
    }
    
    @SafeVarargs
    public static List<Resource> allResources(
    		final ResourceLoader resourceLoader,
    		final String basePath,
    		final Predicate<Resource>... filters) throws Exception {
        
        final var searchPath = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX
                + (basePath == null ? "" : basePath)
                + "/**/*";
        
        final Resource[] resources;
        synchronized (cache) {
            final var cachedResources = cache.get(searchPath);
            if (cachedResources != null) {
                resources = cachedResources;
            } else {
                resources = getResourcePatternResolver(resourceLoader).getResources(searchPath);
            }
        }
        
        final List<Resource> result = new LinkedList<>();
        
        for (final var resource : resources) {
            if (resource.isReadable()) {
                boolean complies = true;
                for (Predicate<Resource> filter : filters) {
                    if (!filter.test(resource)) {
                        complies = false;
                        break;
                    }
                }
                if (complies) {
                    result.add(resource);
                }
            }
        }
        
        return result;
        
    }

    @SafeVarargs
    public static List<Class<?>> allClasses(
    		final String basePackage,
    		final Predicate<MetadataReader>... filters) throws Exception {
    	
    	return allClasses(null, basePackage, filters);
    	
    }

    @SafeVarargs
    public static List<Class<?>> allClasses(
    		final ResourceLoader resourceLoader,
    		final String basePackage,
    		final Predicate<MetadataReader>... filters) throws Exception {

        final var packageSearchPath = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX + resolveBasePackage(basePackage)
            + "/**/*.class";

		final List<Class<?>> classes = new LinkedList<>();
		
		final var resourcePatternResolver = getResourcePatternResolver(null);
	    final var metadataReaderFactory = new CachingMetadataReaderFactory(resourcePatternResolver);

        final Resource[] resources;
        synchronized (cache) {
            final var cachedResources = cache.get(packageSearchPath);
            if (cachedResources != null) {
                resources = cachedResources;
            } else {
                resources = resourcePatternResolver.getResources(packageSearchPath);
            }
        }

		for (Resource resource : resources) {
			if (resource.isReadable()) {
                final var metadataReader = metadataReaderFactory.getMetadataReader(resource);
                boolean complies = true;
                for (Predicate<MetadataReader> filter : filters) {
                    if (!filter.test(metadataReader)) {
                        complies = false;
                        break;
                    }
                }
                if (complies) {
                    try {
                        Class<? extends Object> c = Class.forName(metadataReader.getClassMetadata().getClassName());
                        classes.add(c);
                    } catch (NoClassDefFoundError e) {
                        logger.debug("NoClassDefFoundError: it might be an optional dependency", e);
                    }
                }
			}
		}

		return classes;
		
	}
	
    private static String resolveBasePackage(final String basePackage) {
    	
        return ClassUtils.convertClassNameToResourcePath(SystemPropertyUtils.resolvePlaceholders(basePackage));
    
    }

}
