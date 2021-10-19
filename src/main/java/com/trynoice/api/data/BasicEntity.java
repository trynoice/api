package com.trynoice.api.data;

import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.MappedSuperclass;
import javax.persistence.PrePersist;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.Version;
import java.io.Serializable;
import java.util.Calendar;

/**
 * <p>
 * {@link BasicEntity} is a {@link MappedSuperclass mapped superclass} that contains the following
 * common fields that <i>should</i> be present in all non-join tables.
 * </p>
 * <ol>
 *     <li>{@link BasicEntity#id} - an incrementing (sequence) primary key of the table</li>
 *     <li>{@link BasicEntity#createdAt} - the creation timestamp of the row</li>
 *     <li>{@link BasicEntity#deletedAt} - the deletion timestamp of the row</li>
 *     <li>{@link BasicEntity#version} - optimistic lock used by the JPA during update queries</li>
 * </ol>
 *
 * <p>
 * To enable soft deletes, clients must use {@link BasicEntityCRUDRepository} for database
 * interactions.
 * </p>
 *
 * <p>
 * Example:
 *
 * <pre>
 *     {@code
 *     @Entity
 *     class User extends BasicEntity<Integer> {
 *         private String email;
 *         // other properties ...
 *     }
 *
 *     interface UserRepository extends BasicEntityCRUDRepository<User, Integer>{
 *     }
 *     }
 * </pre>
 * </p>
 *
 * @param <ID> a serializable type representing the primary key the table, typically {@link Long} or
 *             {@link Integer}.
 */
@MappedSuperclass
@Data
@NoArgsConstructor
public class BasicEntity<ID extends Serializable> {

    static final String SOFT_DELETE_FIELD = "deletedAt";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @NonNull
    @Setter(AccessLevel.NONE)
    private ID id;

    @Column(updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    @NonNull
    @Setter(AccessLevel.NONE)
    private Calendar createdAt;

    @Temporal(TemporalType.TIMESTAMP)
    @Setter(AccessLevel.PACKAGE)
    private Calendar deletedAt;

    @Version
    @NonNull
    @Setter(AccessLevel.NONE)
    private Long version;

    @PrePersist
    void setCreatedAt() {
        this.createdAt = Calendar.getInstance();
    }
}
