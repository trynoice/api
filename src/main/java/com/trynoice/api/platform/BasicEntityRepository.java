package com.trynoice.api.platform;

import lombok.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * <p>
 * {@link BasicEntityRepository} is a direct descendant of Spring's {@link
 * PagingAndSortingRepository}. It implements soft-deletes for the descendants of {@link
 * BasicEntity}. Internally, undeleted and soft-deleted entities are referred to as active and
 * inactive respectively.</p>
 * <p>
 * All methods from the {@link PagingAndSortingRepository} are overridden to account for inactive
 * entities. For example, {@link BasicEntityRepository#findAll()} will not return inactive entities
 * (whose {@link BasicEntity#getDeletedAt()} timestamp is not {@literal null}).</p>
 * <p>
 * {@link BasicEntityRepository} <b>doesn't support cascaded operations</b>. Any cascaded updates
 * and deletes must be manually handled by the clients.</p>
 *
 * @param <T>  type of the {@link BasicEntity}.
 * @param <ID> type of the ID for {@link BasicEntity}.
 */
@NoRepositoryBean
public interface BasicEntityRepository<T extends BasicEntity, ID extends Serializable>
    extends PagingAndSortingRepository<T, ID> {

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
    @Query("select case when count(e) > 0 then true else false end from #{#entityName} e where e.id = ?1 and" + WHERE_ACTIVE_CLAUSE)
    boolean existsById(@NonNull ID id);

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
     * Returns all undeleted instances sorted by the given options.
     *
     * @param sort sort options for the query.
     * @return all entities sorted by the given options.
     */
    @Override
    @NonNull
    @Transactional(readOnly = true)
    @Query("select e from #{#entityName} e where" + WHERE_ACTIVE_CLAUSE)
    Iterable<T> findAll(@NonNull Sort sort);

    /**
     * Returns a {@link Page} of undeleted instances meeting the paging restriction provided in the
     * {@code Pageable} object.
     *
     * @param pageable pagination options for the query.
     * @return a page of entities
     */
    @Override
    @NonNull
    @Transactional(readOnly = true)
    @Query("select e from #{#entityName} e where" + WHERE_ACTIVE_CLAUSE)
    Page<T> findAll(@NonNull Pageable pageable);

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
    @Query("update #{#entityName} e set" + SET_INACTIVE_CLAUSE + "where e = ?1 and" + WHERE_ACTIVE_CLAUSE)
    void delete(@NonNull T entity);

    /**
     * Marks the given entity as undeleted.
     *
     * @param entity must not be {@literal null}.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update #{#entityName} e set" + SET_ACTIVE_CLAUSE + "where e = ?1 and" + WHERE_INACTIVE_CLAUSE)
    void undelete(@NonNull T entity);

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
    @Query("update #{#entityName} e set" + SET_INACTIVE_CLAUSE + "where e in ?1 and" + WHERE_ACTIVE_CLAUSE)
    void deleteAll(@NonNull Iterable<? extends T> entities);

    /**
     * Marks the given entities as undeleted.
     *
     * @param entities must not be {@literal null}. Must not contain {@literal null} elements.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update #{#entityName} e set" + SET_ACTIVE_CLAUSE + "where e in ?1 and" + WHERE_INACTIVE_CLAUSE)
    void undeleteAll(@NonNull Iterable<? extends T> entities);

    @Override
    default void deleteAll() {
        throw new UnsupportedOperationException("deleting all entities is not supported");
    }

    /**
     * Permanently removes any soft-deleted entities from the database that were marked as deleted
     * before the {@code deletedBefore} timestamp.
     *
     * @param deletedBefore a not {@literal null} timestamp to remove entities that were marked as
     *                      deleted before this instant.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query("delete from #{#entityName} e where e.deletedAt < ?1")
    void removeAllDeleted(@NonNull OffsetDateTime deletedBefore);
}
