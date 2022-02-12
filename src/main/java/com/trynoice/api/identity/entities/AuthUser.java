package com.trynoice.api.identity.entities;

import com.trynoice.api.platform.BasicEntity;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import java.time.LocalDateTime;

/**
 * A data access object that maps to the {@code auth_user} table in the database.
 */
@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AuthUser extends BasicEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @NonNull
    private String email;

    @NonNull
    private String name;

    @NonNull
    @Builder.Default
    @Setter(AccessLevel.NONE)
    private LocalDateTime lastActiveAt = LocalDateTime.now();

    private short incompleteSignInAttempts;

    private LocalDateTime lastSignInAttemptAt;

    @PrePersist
    @PreUpdate
    void setLastActiveAt() {
        this.lastActiveAt = LocalDateTime.now();
    }

    public void updateSignInAttemptData() {
        this.incompleteSignInAttempts++;
        this.lastSignInAttemptAt = LocalDateTime.now();
    }

    public void resetSignInAttemptData() {
        this.incompleteSignInAttempts = 0;
        this.lastSignInAttemptAt = null;
    }
}
