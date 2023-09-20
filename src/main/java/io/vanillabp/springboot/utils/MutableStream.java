package io.vanillabp.springboot.utils;

import java.util.function.Function;
import java.util.stream.Stream;

public class MutableStream<T> {
    
    private Stream<T> stream;

    private MutableStream(
            final Stream<T> stream) {
        
        this.stream = stream;
        
    }
    
    public static final <T> MutableStream<T> from(
            final Stream<T> stream) {
        
        return new MutableStream<T>(stream);
        
    }

    public Stream<T> getStream() {
        return stream;
    }
    
    public void apply(
            Function<Stream<T>, Stream<T>> action) {
        
        this.stream = action.apply(stream);
        
    }
    
}
