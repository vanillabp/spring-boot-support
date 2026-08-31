package io.vanillabp.springboot.it;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TestAggregateRepository extends JpaRepository<TestAggregate, String> {

}
