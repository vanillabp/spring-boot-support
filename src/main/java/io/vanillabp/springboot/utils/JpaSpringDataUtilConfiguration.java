package io.vanillabp.springboot.utils;

import io.vanillabp.springboot.adapter.SpringDataUtil;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.JpaContext;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;

@AutoConfiguration(after = HibernateJpaAutoConfiguration.class)
@ConditionalOnClass(JpaContext.class)
@ConditionalOnBean(EntityManagerFactory.class)
public class JpaSpringDataUtilConfiguration {

    public static final String BEANNAME_SPRINGDATAUTIL = "jpaSpringDataUtil";

    private final ApplicationContext applicationContext;

    private final LocalContainerEntityManagerFactoryBean containerEntityManagerFactoryBean;

    private final JpaContext jpaContext;

    public JpaSpringDataUtilConfiguration(
            final ApplicationContext applicationContext,
            final LocalContainerEntityManagerFactoryBean containerEntityManagerFactoryBean,
            final JpaContext jpaContext) {

        this.applicationContext = applicationContext;
        this.containerEntityManagerFactoryBean = containerEntityManagerFactoryBean;
        this.jpaContext = jpaContext;

    }

    @Bean(name = BEANNAME_SPRINGDATAUTIL)
    @ConditionalOnMissingBean(SpringDataUtil.class)
    public SpringDataUtil jpaSpringDataUtil() {

        return new JpaSpringDataUtil(
                applicationContext,
                jpaContext,
                containerEntityManagerFactoryBean);

    }

}
