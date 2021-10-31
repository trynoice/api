package com.trynoice.api.identity.models;

import com.trynoice.api.platform.BasicEntity;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import lombok.ToString;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.OneToMany;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * A data access object that maps to the {@code auth_user} table in the database.
 */
@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AuthUser extends BasicEntity<Integer> {

    @NonNull
    private String email;

    @NonNull
    private String name;

    @NonNull
    @Builder.Default
    @Setter(AccessLevel.NONE)
    private LocalDateTime lastActiveAt = LocalDateTime.now();

    @NonNull
    @Builder.Default
    private Short signInAttempts = 0;

    @ToString.Exclude
    @OneToMany(mappedBy = "owner", fetch = FetchType.LAZY)
    private List<RefreshToken> refreshTokens;

    @PrePersist
    @PreUpdate
    void setLastActiveAt() {
        this.lastActiveAt = LocalDateTime.now();
    }

    public void incrementSignInAttempts() {
        this.signInAttempts++;
    }

    public void resetSignInAttempts() {
        this.signInAttempts = 0;
    }
}
