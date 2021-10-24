package com.trynoice.api.platform;

import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Stack;

import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class BasicEntityCrudRepositoryTest {

    private Stack<TestEntity> activeEntityStack;
    private Stack<TestEntity> inactiveEntityStack;

    @Autowired
    private TestEntityRepository repository;

    @BeforeEach
    void setUp() {
        activeEntityStack = new Stack<>();
        inactiveEntityStack = new Stack<>();

        for (int i = 0; i < 5; i++) {
            activeEntityStack.push(repository.save(new TestEntity()));

            val entity = new TestEntity();
            entity.setDeletedAt(LocalDateTime.now());
            inactiveEntityStack.push(repository.save(entity));
        }
    }

    @Test
    void findAllActive() {
        assertEquals(activeEntityStack, repository.findAllActive());
    }

    @Test
    void findAllInactive() {
        assertEquals(inactiveEntityStack, repository.findAllInactive());
    }

    @Test
    void findActiveById() {
        var result = repository.findActiveById(activeEntityStack.peek().getId());
        assertTrue(result.isPresent());
        assertEquals(activeEntityStack.peek(), result.get());

        result = repository.findActiveById(inactiveEntityStack.peek().getId());
        assertFalse(result.isPresent());
    }

    @Test
    void findInactiveById() {
        var result = repository.findInactiveById(inactiveEntityStack.peek().getId());
        assertTrue(result.isPresent());
        assertEquals(inactiveEntityStack.peek(), result.get());

        result = repository.findInactiveById(activeEntityStack.peek().getId());
        assertFalse(result.isPresent());
    }

    @Test
    void countActive() {
        assertEquals(activeEntityStack.size(), repository.countActive());
    }

    @Test
    void countInactive() {
        assertEquals(inactiveEntityStack.size(), repository.countInactive());
    }

    @Test
    void existsActiveById() {
        assertTrue(repository.existsActiveById(activeEntityStack.peek().getId()));
        assertFalse(repository.existsActiveById(inactiveEntityStack.peek().getId()));
    }

    @Test
    void existsInactiveById() {
        assertTrue(repository.existsInactiveById(inactiveEntityStack.peek().getId()));
        assertFalse(repository.existsInactiveById(activeEntityStack.peek().getId()));
    }

    @Test
    void deleteById() {
        val entity = activeEntityStack.pop();
        repository.deleteById(entity.getId());
        assertInactive(entity.getId());
    }

    @Test
    void undeleteByID() {
        val entity = inactiveEntityStack.pop();
        repository.undeleteByID(entity.getId());
        assertActive(entity.getId());
    }

    @Test
    void delete() {
        val entity = activeEntityStack.pop();
        repository.delete(entity);
        assertInactive(entity.getId());
    }

    @Test
    void undelete() {
        val entity = inactiveEntityStack.pop();
        repository.undelete(entity);
        assertActive(entity.getId());
    }

    @Test
    void deleteAll() {
        val entities = asList(activeEntityStack.pop(), activeEntityStack.pop());
        repository.deleteAll(entities);
        entities.forEach(entity -> assertInactive(entity.getId()));
    }

    @Test
    void undeleteAll() {
        val entities = asList(inactiveEntityStack.pop(), inactiveEntityStack.pop());
        repository.undeleteAll(entities);
        entities.forEach(entity -> assertActive(entity.getId()));
    }

    @Test
    void deleteAllById() {
        val ids = asList(activeEntityStack.pop().getId(), activeEntityStack.pop().getId());
        repository.deleteAllById(ids);
        ids.forEach(this::assertInactive);
    }

    @Test
    void undeleteAllById() {
        val ids = asList(inactiveEntityStack.pop().getId(), inactiveEntityStack.pop().getId());
        repository.undeleteAllById(ids);
        ids.forEach(this::assertActive);
    }

    @Test
    void deleteAll_global() {
        assertThrows(UnsupportedOperationException.class, () -> repository.deleteAll());
    }

    private void assertActive(int entityId) {
        val dbEntity = repository.findById(entityId).orElseThrow();
        assertNull(dbEntity.getDeletedAt());
    }

    private void assertInactive(int entityId) {
        val dbEntity = repository.findById(entityId).orElseThrow();
        assertNotNull(dbEntity.getDeletedAt());
    }
}
