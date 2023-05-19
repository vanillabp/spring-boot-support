package io.vanillabp.springboot.adapter;

import io.vanillabp.spi.process.ProcessService;
import org.springframework.data.repository.CrudRepository;

public interface ProcessServiceImplementation<DE> extends ProcessService<DE> {

    Class<DE> getWorkflowAggregateClass();

    CrudRepository<DE, ?> getWorkflowAggregateRepository();
    
    void setParent(AdapterAwareProcessService<DE> parent);

}
