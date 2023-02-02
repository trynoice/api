package com.trynoice.api.identity.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * A data access object that maps to the {@code auth_user} table in the database.
 */
@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @NonNull
    @Column(updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Version
    private long version;

    @NonNull
    private String email;

    @NonNull
    private String name;

    @NonNull
    @Builder.Default
    @Setter(AccessLevel.NONE)
    private OffsetDateTime lastActiveAt = OffsetDateTime.now();

    private short incompleteSignInAttempts;

    private OffsetDateTime lastSignInAttemptAt;

    private OffsetDateTime deactivatedAt;

    public void updateLastActiveTimestamp() {
        this.lastActiveAt = OffsetDateTime.now();
    }

    public void updateSignInAttemptData() {
        this.incompleteSignInAttempts++;
        this.lastSignInAttemptAt = OffsetDateTime.now();
    }

    public void resetSignInAttemptData() {
        this.incompleteSignInAttempts = 0;
        this.lastSignInAttemptAt = null;
    }
}
