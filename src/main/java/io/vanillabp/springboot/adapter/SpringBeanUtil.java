package io.vanillabp.springboot.adapter;

import io.vanillabp.spi.service.WorkflowService;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

import java.util.Map;

public class SpringBeanUtil {

    private final ApplicationContext applicationContext;

    private Map<String, Object> workflowAnnotatedBeansCache;

    private boolean cached = true;

    public SpringBeanUtil(
            final ApplicationContext applicationContext) {

        this.applicationContext = applicationContext;

    }

    public Map<String, Object> getWorkflowAnnotatedBeans() {

        synchronized (this) {
            if (workflowAnnotatedBeansCache != null) {
                return workflowAnnotatedBeansCache;
            }
            final var beans = applicationContext
                    .getBeansWithAnnotation(WorkflowService.class);
            if (cached) {
                workflowAnnotatedBeansCache = beans;
            }
            return beans;
        }

    }

    @EventListener
    void onApplicationEvent(
            final ContextRefreshedEvent event) {

        synchronized (this) {
            cached = false; // only cache during startup
            workflowAnnotatedBeansCache = null;
        }

    }

}
