package io.vanillabp.springboot.parameters;

import io.vanillabp.spi.service.MultiInstanceElementResolver;

public class ResolverBasedMultiInstanceMethodParameter extends MethodParameter {

    protected final MultiInstanceElementResolver<?, ?> resolverBean;

    public ResolverBasedMultiInstanceMethodParameter(
            final String parameter,
            final MultiInstanceElementResolver<?, ?> resolverBean) {

        super(parameter);
        this.resolverBean = resolverBean;
        
    }
    
    public MultiInstanceElementResolver<?, ?> getResolverBean() {
        return resolverBean;
    }
    
}
