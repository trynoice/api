package com.trynoice.api.identity.models;

import com.trynoice.api.platform.BasicEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.lang.NonNull;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import java.time.LocalDateTime;

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

    @NonNull
    @ManyToOne(optional = false)
    private AuthUser owner;

    @NonNull
    @Builder.Default
    private String userAgent = "";

    @NonNull
    private LocalDateTime expiresAt;
}
