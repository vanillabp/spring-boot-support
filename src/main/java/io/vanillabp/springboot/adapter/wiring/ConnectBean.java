package io.vanillabp.springboot.adapter.wiring;

import java.lang.reflect.Method;
import java.util.List;

import io.vanillabp.springboot.parameters.MethodParameter;

@FunctionalInterface
public interface ConnectBean {

    void connect(Object bean, Method method, List<MethodParameter> parameters);
    
}
