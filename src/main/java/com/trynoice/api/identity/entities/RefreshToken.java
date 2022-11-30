package com.trynoice.api.identity.entities;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.time.OffsetDateTime;
import java.util.Date;

/**
 * A data access object that maps to the {@code refresh_token} table in the database.
 */
@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken {

    public static final String ORD_JWT_CLAIM = "ord";

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
    @ManyToOne(optional = false)
    private AuthUser owner;

    @NonNull
    @Builder.Default
    private String userAgent = "";

    private long ordinal;

    @NonNull
    private OffsetDateTime expiresAt;

    public void incrementOrdinal() {
        ordinal++;
    }

    /**
     * Builds, signs and returns a JWT for this refresh token.
     *
     * @param signingAlgorithm algorithm instance for signing the JWT.
     * @return a signed JWT string for this refresh token.
     */
    @NonNull
    public String toSignedJwt(@NonNull Algorithm signingAlgorithm) {
        return JWT.create()
            .withJWTId(String.valueOf(getId()))
            .withIssuedAt(Date.from(getCreatedAt().toInstant()))
            .withExpiresAt(Date.from(expiresAt.toInstant()))
            .withClaim(ORD_JWT_CLAIM, ordinal)
            .sign(signingAlgorithm);
    }
}
