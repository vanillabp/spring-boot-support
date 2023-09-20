package io.vanillabp.springboot.utils;

@FunctionalInterface
public interface TriFunction<A, B, C, R> {

    R apply(A a, B b, C c);
    
}
