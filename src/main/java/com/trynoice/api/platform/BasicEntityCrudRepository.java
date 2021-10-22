package com.trynoice.api.platform;

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
 * {@link BasicEntityCrudRepository} is a direct descendant of Spring's {@link CrudRepository}. It
 * implements soft-deletes for the descendants of {@link BasicEntity}. Soft deleted entities are
 * referred to as inactive while the undeleted entities are called active. </p>
 * <p>
 * All methods from the {@link CrudRepository} retain their original behaviour except {@code
 * delete*} methods. The {@code delete*} methods' default behaviour is overridden to support soft
 * deletes. All the other methods from {@link CrudRepository} are <b>unaware of soft-deletes</b>.
 * The {@link BasicEntityCrudRepository} doesn't support hard deletes at all. Moreover, it adds new
 * methods to find, count and check existence of active and inactive entities, e.g. {@link
 * BasicEntityCrudRepository#countActive() countActive()}. </p>
 * <p>
 * {@link BasicEntityCrudRepository} <b>doesn't support cascaded operations</b>. Any cascaded
 * updates and deletes must be manually handled by the clients. </p>
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

    @NonNull
    @Transactional(readOnly = true)
    @Query("select e from #{#entityName} e where" + WHERE_ACTIVE_CLAUSE)
    List<T> findAllActive();

    @NonNull
    @Transactional(readOnly = true)
    @Query("select e from #{#entityName} e where" + WHERE_INACTIVE_CLAUSE)
    List<T> findAllInactive();

    @NonNull
    @Transactional(readOnly = true)
    @Query("select e from #{#entityName} e where e.id = ?1 and" + WHERE_ACTIVE_CLAUSE)
    Optional<T> findActiveById(ID id);

    @NonNull
    @Transactional(readOnly = true)
    @Query("select e from #{#entityName} e where e.id = ?1 and" + WHERE_INACTIVE_CLAUSE)
    Optional<T> findInactiveById(ID id);

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

    @Override
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("update #{#entityName} e set" + SET_INACTIVE_CLAUSE + "where e.id = ?1 and" + WHERE_ACTIVE_CLAUSE)
    void deleteById(@NonNull ID id);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("update #{#entityName} e set" + SET_ACTIVE_CLAUSE + "where e.id = ?1 and" + WHERE_INACTIVE_CLAUSE)
    void undeleteByID(@NonNull ID id);

    @Override
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("update #{#entityName} e set" + SET_INACTIVE_CLAUSE + "where e.id = :#{#p.id} and" + WHERE_ACTIVE_CLAUSE)
    void delete(@Param("p") @NonNull T entity);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("update #{#entityName} e set" + SET_ACTIVE_CLAUSE + "where e.id = :#{#p.id} and" + WHERE_INACTIVE_CLAUSE)
    void undelete(@Param("p") @NonNull T entity);

    @Override
    @Modifying(clearAutomatically = true)
    @Transactional
    default void deleteAll(@NonNull Iterable<? extends T> entities) {
        entities.forEach(this::delete);
    }

    @Modifying(clearAutomatically = true)
    @Transactional
    default void undeleteAll(@NonNull Iterable<? extends T> entities) {
        entities.forEach(this::undelete);
    }

    @Override
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("update #{#entityName} e set" + SET_INACTIVE_CLAUSE + "where e.id in ?1 and" + WHERE_ACTIVE_CLAUSE)
    void deleteAllById(@NonNull Iterable<? extends ID> ids);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("update #{#entityName} e set" + SET_ACTIVE_CLAUSE + "where e.id in ?1 and" + WHERE_INACTIVE_CLAUSE)
    void undeleteAllById(@NonNull Iterable<? extends ID> ids);

    @Override
    default void deleteAll() {
        throw new UnsupportedOperationException();
    }
}
