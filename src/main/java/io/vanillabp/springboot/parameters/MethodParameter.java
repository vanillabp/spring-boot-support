package io.vanillabp.springboot.parameters;

public abstract class MethodParameter {

    private final int index;
    
    private final String parameter;
    
    public MethodParameter(
            final int index,
            final String parameter) {
        
        this.index = index;
        this.parameter = parameter;
        
    }
    
    public String getParameter() {
        
        return parameter;
        
    }
    
    public int getIndex() {
        
        return index;
        
    }
    
}
