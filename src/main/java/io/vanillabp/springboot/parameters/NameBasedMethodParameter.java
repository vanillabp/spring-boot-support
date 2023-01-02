package io.vanillabp.springboot.parameters;

public abstract class NameBasedMethodParameter extends MethodParameter {

    protected final String name;
    
    public NameBasedMethodParameter(
            final String name) {
        
        this.name = name;
        
    }
    
    public String getName() {
        
        return name;
        
    }
    
}
