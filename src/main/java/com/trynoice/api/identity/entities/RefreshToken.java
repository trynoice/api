package com.trynoice.api.identity.entities;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.trynoice.api.platform.BasicEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
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
public class RefreshToken extends BasicEntity {

    public static final String ORD_JWT_CLAIM = "ord";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @NonNull
    @ManyToOne(optional = false)
    private AuthUser owner;

    @NonNull
    @Builder.Default
    private String userAgent = "";

    private long ordinal;

    @NonNull
    private LocalDateTime expiresAt;

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
    public String getJwt(@NonNull Algorithm signingAlgorithm) {
        return JWT.create()
            .withJWTId(String.valueOf(getId()))
            .withIssuedAt(convertLocalDateTimeToDate(getCreatedAt()))
            .withExpiresAt(convertLocalDateTimeToDate(expiresAt))
            .withClaim(ORD_JWT_CLAIM, ordinal)
            .sign(signingAlgorithm);
    }

    @NonNull
    private static Date convertLocalDateTimeToDate(@NonNull LocalDateTime localDateTime) {
        return Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
    }
}
