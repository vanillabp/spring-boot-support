package io.vanillabp.springboot.parameters;

public abstract class MethodParameter {

    private final String parameter;
    
    public MethodParameter(
            final String parameter) {
        
        this.parameter = parameter;
        
    }
    
    public String getName() {
        
        return parameter;
        
    }
    
}
