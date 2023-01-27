package io.vanillabp.springboot.utils;

import io.vanillabp.springboot.adapter.SpringDataUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.JpaContext;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;

@Configuration
@ConditionalOnMissingBean(SpringDataUtil.class)
public class JpaSpringDataUtilConfiguration {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private LocalContainerEntityManagerFactoryBean containerEntityManagerFactoryBean;

    @Autowired
    private JpaContext jpaContext;
    
    @Bean
    public SpringDataUtil jpaSpringDataUtil() {
       
        return new JpaSpringDataUtil(
                applicationContext,
                jpaContext,
                containerEntityManagerFactoryBean);
        
    }
    
}
