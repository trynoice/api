package com.trynoice.api.platform;

import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.MappedSuperclass;
import javax.persistence.PrePersist;
import java.time.OffsetDateTime;

/**
 * <p>
 * {@link BasicEntity} is a {@link MappedSuperclass mapped superclass} that contains the following
 * common fields that <i>should</i> be present in all child entities.
 * </p>
 * <ol>
 *     <li>{@link BasicEntity#createdAt} - the creation timestamp of the row</li>
 *     <li>{@link BasicEntity#deletedAt} - the deletion timestamp of the row</li>
 *     <li>{@link BasicEntity#version} - optimistic lock used by the JPA during update queries</li>
 * </ol>
 *
 * <p>
 * To enable soft deletes, clients must use {@link BasicEntityRepository} for database
 * interactions.
 * </p>
 *
 * <p>
 * Example:
 *
 * <pre>
 *     {@code
 *     @Entity
 *     class User extends BasicEntity {
 *
 *         @Id
 *         @GeneratedValue(strategy = GenerationType.IDENTITY)
 *         private Integer id;
 *
 *         private String email;
 *         // other properties ...
 *     }
 *
 *     interface UserRepository extends BasicEntityRepository&lt;User, Integer&gt;{
 *     }
 *     }
 * </pre>
 * </p>
 */
@MappedSuperclass
@Data
@NoArgsConstructor
public class BasicEntity {

    static final String SOFT_DELETE_FIELD = "deletedAt";

    /**
     * Creation timestamp of this row in the table.
     */
    @NonNull
    @Column(updatable = false)
    private OffsetDateTime createdAt;

    /**
     * Deletion timestamp of this row in the table. It is used for facilitating soft-deletes.
     */
    @Setter(AccessLevel.PACKAGE)
    private OffsetDateTime deletedAt;

    /**
     * Optimistic lock used by the JPA operations.
     */
    private long version;

    @PrePersist
    void setCreatedAt() {
        this.createdAt = OffsetDateTime.now();
    }
}
