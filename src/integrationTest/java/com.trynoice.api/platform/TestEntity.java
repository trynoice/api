package com.trynoice.api.platform;

import org.springframework.boot.test.context.TestComponent;

import javax.persistence.Entity;

@Entity
@TestComponent
public class TestEntity extends BasicEntity<Integer> {
}
