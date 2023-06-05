package io.vanillabp.springboot.adapter.wiring;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.Stream;

public class MutableParamterStream {
    
    private Stream<Parameter> stream;

    private MutableParamterStream(
            final Stream<Parameter> stream) {
        
        this.stream = stream;
        
    }
    
    public static final MutableParamterStream fromMethod(
            final Method method) {
        
        return new MutableParamterStream(
                Arrays.stream(method.getParameters()));
        
    }

    public Stream<Parameter> getStream() {
        return stream;
    }
    
    public void apply(
            Function<Stream<Parameter>, Stream<Parameter>> action) {
        
        this.stream = action.apply(stream);
        
    }
    
}
