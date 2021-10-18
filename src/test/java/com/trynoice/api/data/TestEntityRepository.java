package com.trynoice.api.data;

import org.springframework.boot.test.context.TestComponent;

@TestComponent
public interface TestEntityRepository extends BasicEntityCRUDRepository<TestEntity, Integer> {
}
