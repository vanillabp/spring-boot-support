package io.vanillabp.springboot.parameters;

public abstract class NameBasedMethodParameter extends MethodParameter {

    protected final String name;
    
    public NameBasedMethodParameter(
            final int index,
            final String parameter,
            final String name) {
        
        super(index, parameter);
        this.name = name;
        
    }
    
    public String getName() {
        
        return name;
        
    }
    
}
