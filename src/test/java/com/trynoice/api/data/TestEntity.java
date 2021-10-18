package com.trynoice.api.data;

import org.springframework.boot.test.context.TestComponent;

import javax.persistence.Entity;

@Entity
@TestComponent
public class TestEntity extends BasicEntity<Integer> {
}
