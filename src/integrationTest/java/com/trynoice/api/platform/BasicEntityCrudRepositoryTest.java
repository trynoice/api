package com.trynoice.api.platform;

import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;
import java.util.Stack;

import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
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
    void findAll() {
        assertEquals(activeEntityStack, repository.findAll());
    }

    @Test
    void findById() {
        var result = repository.findById(activeEntityStack.peek().getId());
        assertTrue(result.isPresent());
        assertEquals(activeEntityStack.peek(), result.get());

        result = repository.findById(inactiveEntityStack.peek().getId());
        assertFalse(result.isPresent());
    }

    @Test
    void count() {
        assertEquals(activeEntityStack.size(), repository.count());
    }

    @Test
    void existsById() {
        assertTrue(repository.existsById(activeEntityStack.peek().getId()));
        assertFalse(repository.existsById(inactiveEntityStack.peek().getId()));
    }

    @Test
    void deleteById() {
        val entity = activeEntityStack.pop();
        repository.deleteById(entity.getId());
        assertFalse(repository.existsById(entity.getId()));
    }

    @Test
    void undeleteByID() {
        val entity = inactiveEntityStack.pop();
        repository.undeleteByID(entity.getId());
        assertTrue(repository.existsById(entity.getId()));
    }

    @Test
    void delete() {
        val entity = activeEntityStack.pop();
        repository.delete(entity);
        assertFalse(repository.existsById(entity.getId()));
    }

    @Test
    void undelete() {
        val entity = inactiveEntityStack.pop();
        repository.undelete(entity);
        assertTrue(repository.existsById(entity.getId()));
    }

    @Test
    void deleteAll() {
        val entities = asList(activeEntityStack.pop(), activeEntityStack.pop());
        repository.deleteAll(entities);
        entities.forEach(e -> assertFalse(repository.existsById(e.getId())));
    }

    @Test
    void undeleteAll() {
        val entities = asList(inactiveEntityStack.pop(), inactiveEntityStack.pop());
        repository.undeleteAll(entities);
        entities.forEach(e -> assertTrue(repository.existsById(e.getId())));
    }

    @Test
    void deleteAllById() {
        val ids = asList(activeEntityStack.pop().getId(), activeEntityStack.pop().getId());
        repository.deleteAllById(ids);
        ids.forEach(id -> assertFalse(repository.existsById(id)));
    }

    @Test
    void undeleteAllById() {
        val ids = asList(inactiveEntityStack.pop().getId(), inactiveEntityStack.pop().getId());
        repository.undeleteAllById(ids);
        ids.forEach(id -> assertTrue(repository.existsById(id)));
    }

    @Test
    void deleteAll_global() {
        assertThrows(UnsupportedOperationException.class, () -> repository.deleteAll());
    }
}
