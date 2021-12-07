package com.trynoice.api.identity.models;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
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
import javax.persistence.ManyToOne;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * A data access object that maps to the {@code refresh_token} table in the database.
 */
@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class RefreshToken extends BasicEntity<Long> {

    public static final String ORD_JWT_CLAIM = "ord";

    @NonNull
    @ManyToOne(optional = false)
    private AuthUser owner;

    @NonNull
    @Builder.Default
    private String userAgent = "";

    @NonNull
    private LocalDateTime expiresAt;

    @NonNull
    @Setter(AccessLevel.NONE)
    @Builder.Default
    private LocalDateTime lastUsedAt = LocalDateTime.now();

    @PrePersist
    @PreUpdate
    void setLastUsedAt() {
        this.lastUsedAt = LocalDateTime.now();
    }

    /**
     * Builds, signs and returns a JWT for this refresh token.
     *
     * @param signingAlgorithm algorithm instance for signing the JWT.
     * @return a signed JWT string for this refresh token.
     */
    @NonNull
    public String getJwt(@NonNull Algorithm signingAlgorithm) {
        return JWT.create()
            .withJWTId(getId().toString())
            .withIssuedAt(convertLocalDateTimeToDate(getCreatedAt()))
            .withExpiresAt(convertLocalDateTimeToDate(expiresAt))
            .withClaim(ORD_JWT_CLAIM, getVersion())
            .sign(signingAlgorithm);
    }

    @NonNull
    private static Date convertLocalDateTimeToDate(@NonNull LocalDateTime localDateTime) {
        return Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
    }
}
