package com.trynoice.api.platform;

import lombok.NonNull;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * <p>
 * {@link BasicEntityCrudRepository} is a direct descendant of Spring's {@link CrudRepository}. It
 * implements soft-deletes for the descendants of {@link BasicEntity}. Internally, undeleted and
 * soft-deleted entities are referred to as active and inactive respectively.</p>
 * <p>
 * All methods from the {@link CrudRepository} are overridden to account for inactive entities. For
 * example, {@link BasicEntityCrudRepository#findAll()} will not return inactive entities (whose
 * {@link BasicEntity#getDeletedAt()} timestamp is not {@literal null}).</p>
 * <p>
 * {@link BasicEntityCrudRepository} <b>doesn't support cascaded operations</b>. Any cascaded
 * updates and deletes must be manually handled by the clients.</p>
 *
 * @param <T>  type of the {@link BasicEntity}.
 * @param <ID> type of the ID for {@link BasicEntity}.
 */
@NoRepositoryBean
public interface BasicEntityCrudRepository<T extends BasicEntity<ID>, ID extends Serializable>
    extends CrudRepository<T, ID> {

    String WHERE_ACTIVE_CLAUSE = " e." + BasicEntity.SOFT_DELETE_FIELD + " is null ";

    String WHERE_INACTIVE_CLAUSE = " e." + BasicEntity.SOFT_DELETE_FIELD + " is not null ";

    String SET_ACTIVE_CLAUSE = " e." + BasicEntity.SOFT_DELETE_FIELD + " = null ";

    String SET_INACTIVE_CLAUSE = " e." + BasicEntity.SOFT_DELETE_FIELD + " = now() ";

    /**
     * Retrieves an undeleted entity by its id.
     *
     * @param id must not be {@literal null}.
     * @return the entity with the given id or {@literal Optional#empty()} if none found.
     */
    @Override
    @NonNull
    @Transactional(readOnly = true)
    @Query("select e from #{#entityName} e where e.id = ?1 and" + WHERE_ACTIVE_CLAUSE)
    Optional<T> findById(@NonNull ID id);

    /**
     * Returns whether an undeleted entity with the given id exists.
     *
     * @param id must not be {@literal null}.
     * @return {@literal true} if an entity with the given id exists, {@literal false} otherwise.
     */
    @Override
    @Transactional(readOnly = true)
    default boolean existsById(@NonNull ID id) {
        return findById(id).isPresent();
    }

    /**
     * Returns all undeleted instances of the type.
     *
     * @return all entities
     */
    @Override
    @NonNull
    @Transactional(readOnly = true)
    @Query("select e from #{#entityName} e where" + WHERE_ACTIVE_CLAUSE)
    Iterable<T> findAll();

    /**
     * Returns all undeleted instances of the type {@code T} with the given IDs.
     * <p>
     * If some or all ids are not found, no entities are returned for these IDs.
     * <p>
     * Note that the order of elements in the result is not guaranteed.
     *
     * @param ids must not be {@literal null} nor contain any {@literal null} values.
     * @return guaranteed to be not {@literal null}. The size can be equal or less than the number
     * of given {@literal ids}.
     */
    @Override
    @NonNull
    @Transactional(readOnly = true)
    @Query("select e from #{#entityName} e where e.id in ?1 and" + WHERE_INACTIVE_CLAUSE)
    Iterable<T> findAllById(@NonNull Iterable<ID> ids);

    /**
     * Returns the number of undeleted entities available.
     *
     * @return the number of entities.
     */
    @Override
    @Transactional(readOnly = true)
    @Query("select count(e) from #{#entityName} e where" + WHERE_ACTIVE_CLAUSE)
    long count();

    /**
     * Marks the entity with the given id as deleted.
     *
     * @param id must not be {@literal null}.
     */
    @Override
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update #{#entityName} e set" + SET_INACTIVE_CLAUSE + "where e.id = ?1 and" + WHERE_ACTIVE_CLAUSE)
    void deleteById(@NonNull ID id);

    /**
     * Marks the entity with the given id as undeleted.
     *
     * @param id must not be {@literal null}.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update #{#entityName} e set" + SET_ACTIVE_CLAUSE + "where e.id = ?1 and" + WHERE_INACTIVE_CLAUSE)
    void undeleteByID(@NonNull ID id);

    /**
     * Marks the given entity as deleted.
     *
     * @param entity must not be {@literal null}.
     */
    @Override
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update #{#entityName} e set" + SET_INACTIVE_CLAUSE + "where e.id = :#{#p.id} and" + WHERE_ACTIVE_CLAUSE)
    void delete(@Param("p") @NonNull T entity);

    /**
     * Marks the given entity as undeleted.
     *
     * @param entity must not be {@literal null}.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update #{#entityName} e set" + SET_ACTIVE_CLAUSE + "where e.id = :#{#p.id} and" + WHERE_INACTIVE_CLAUSE)
    void undelete(@Param("p") @NonNull T entity);

    /**
     * Marks all instances of the type {@code T} with the given IDs as deleted.
     *
     * @param ids must not be {@literal null}. Must not contain {@literal null} elements.
     */
    @Override
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update #{#entityName} e set" + SET_INACTIVE_CLAUSE + "where e.id in ?1 and" + WHERE_ACTIVE_CLAUSE)
    void deleteAllById(@NonNull Iterable<? extends ID> ids);

    /**
     * Marks all instances of the type {@code T} with the given IDs as undeleted.
     *
     * @param ids must not be {@literal null}. Must not contain {@literal null} elements.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update #{#entityName} e set" + SET_ACTIVE_CLAUSE + "where e.id in ?1 and" + WHERE_INACTIVE_CLAUSE)
    void undeleteAllById(@NonNull Iterable<? extends ID> ids);

    /**
     * Marks the given entities as deleted.
     *
     * @param entities must not be {@literal null}. Must not contain {@literal null} elements.
     */
    @Override
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    default void deleteAll(@NonNull Iterable<? extends T> entities) {
        deleteAllById(
            StreamSupport.stream(entities.spliterator(), false)
                .map(BasicEntity::getId)
                .collect(Collectors.toUnmodifiableSet()));
    }

    /**
     * Marks the given entities as undeleted.
     *
     * @param entities must not be {@literal null}. Must not contain {@literal null} elements.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    default void undeleteAll(@NonNull Iterable<? extends T> entities) {
        undeleteAllById(
            StreamSupport.stream(entities.spliterator(), false)
                .map(BasicEntity::getId)
                .collect(Collectors.toUnmodifiableSet()));
    }

    @Override
    default void deleteAll() {
        throw new UnsupportedOperationException("deleting all entities is not supported");
    }
}
