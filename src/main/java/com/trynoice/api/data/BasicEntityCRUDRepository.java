package com.trynoice.api.data;

import lombok.NonNull;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

/**
 * <p>
 * {@link BasicEntityCRUDRepository} is a direct descendant of Spring's {@link CrudRepository}. It
 * implements soft-deletes for the descendants of {@link BasicEntity}. Soft deleted entities are
 * referred to as inactive while the undeleted entities are called active.
 * </p>
 * <p>
 * All methods from the {@link CrudRepository} retain their original behaviour except {@code
 * delete*} methods. The {@code delete*} methods' default behaviour is overridden to support soft
 * deletes. All the other methods from {@link CrudRepository} are <b>unaware of soft-delete</b>.
 * </p>
 * <p>
 * The {@link BasicEntityCRUDRepository} doesn't support hard deletes at all. Moreover, it adds new
 * methods to find, count and check existence of active and inactive entities, e.g. {@link
 * BasicEntityCRUDRepository#countActive() countActive()}.
 * </p>
 *
 * @param <T>  type of the {@link BasicEntity}.
 * @param <ID> type of the ID for {@link BasicEntity}.
 */
@NoRepositoryBean
public interface BasicEntityCRUDRepository<T extends BasicEntity<ID>, ID extends Serializable>
    extends CrudRepository<T, ID> {

    String WHERE_ACTIVE_CLAUSE = " e." + BasicEntity.SOFT_DELETE_FIELD + " is null ";

    String WHERE_INACTIVE_CLAUSE = " e." + BasicEntity.SOFT_DELETE_FIELD + " is not null ";

    String SET_ACTIVE_CLAUSE = " e." + BasicEntity.SOFT_DELETE_FIELD + " = null ";

    String SET_INACTIVE_CLAUSE = " e." + BasicEntity.SOFT_DELETE_FIELD + " = now() ";

    @Transactional(readOnly = true)
    @Query("select e from #{#entityName} e where" + WHERE_ACTIVE_CLAUSE)
    @NonNull List<T> findAllActive();

    @Transactional(readOnly = true)
    @Query("select e from #{#entityName} e where" + WHERE_INACTIVE_CLAUSE)
    @NonNull List<T> findAllInactive();

    @Transactional(readOnly = true)
    @Query("select e from #{#entityName} e where e.id = ?1 and" + WHERE_ACTIVE_CLAUSE)
    @NonNull Optional<T> findActiveById(ID id);

    @Transactional(readOnly = true)
    @Query("select e from #{#entityName} e where e.id = ?1 and" + WHERE_INACTIVE_CLAUSE)
    @NonNull Optional<T> findInactiveById(ID id);

    @Transactional(readOnly = true)
    @Query("select count(e) from #{#entityName} e where" + WHERE_ACTIVE_CLAUSE)
    long countActive();

    @Transactional(readOnly = true)
    @Query("select count(e) from #{#entityName} e where" + WHERE_INACTIVE_CLAUSE)
    long countInactive();

    @Transactional(readOnly = true)
    default boolean existsActiveById(ID id) {
        return findActiveById(id).isPresent();
    }

    @Transactional(readOnly = true)
    default boolean existsInactiveById(ID id) {
        return findInactiveById(id).isPresent();
    }

    @Transactional
    @Modifying
    @Query("update #{#entityName} e set" + SET_INACTIVE_CLAUSE + "where e.id = ?1 and" + WHERE_ACTIVE_CLAUSE)
    @Override
    void deleteById(@NonNull ID id);

    @Transactional
    @Modifying
    @Query("update #{#entityName} e set" + SET_ACTIVE_CLAUSE + "where e.id = ?1 and" + WHERE_INACTIVE_CLAUSE)
    void undeleteByID(@NonNull ID id);

    @Transactional
    @Modifying
    @Query("update #{#entityName} e set" + SET_INACTIVE_CLAUSE + "where e.id = :#{#p.id} and" + WHERE_ACTIVE_CLAUSE)
    @Override
    void delete(@Param("p") @NonNull T entity);

    @Transactional
    @Modifying
    @Query("update #{#entityName} e set" + SET_ACTIVE_CLAUSE + "where e.id = :#{#p.id} and" + WHERE_INACTIVE_CLAUSE)
    void undelete(@Param("p") @NonNull T entity);

    @Transactional
    @Modifying
    @Override
    default void deleteAll(@NonNull Iterable<? extends T> entities) {
        entities.forEach(this::delete);
    }

    @Transactional
    @Modifying
    default void undeleteAll(@NonNull Iterable<? extends T> entities) {
        entities.forEach(this::undelete);
    }

    @Transactional
    @Modifying
    @Query("update #{#entityName} e set" + SET_INACTIVE_CLAUSE + "where e.id in ?1 and" + WHERE_ACTIVE_CLAUSE)
    @Override
    void deleteAllById(@NonNull Iterable<? extends ID> ids);

    @Transactional
    @Modifying
    @Query("update #{#entityName} e set" + SET_ACTIVE_CLAUSE + "where e.id in ?1 and" + WHERE_INACTIVE_CLAUSE)
    void undeleteAllById(@NonNull Iterable<? extends ID> ids);

    @Transactional
    @Modifying
    @Override
    default void deleteAll() {
        throw new UnsupportedOperationException();
    }
}
