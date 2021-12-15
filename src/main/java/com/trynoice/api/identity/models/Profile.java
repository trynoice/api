package com.trynoice.api.identity.models;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A data transfer object to return auth user's profile data back to the controller.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Profile {

    @Schema(required = true, description = "id of the account")
    @NonNull
    private Long accountId;

    @Schema(required = true, description = "name of the user associated with the account")
    @NonNull
    private String name;

    @Schema(required = true, description = "email of the user associated with the account")
    @NonNull
    private String email;

    @Schema(required = true, description = "an array of active sessions (empty array if none exist)")
    @NonNull
    private List<ActiveSessionInfo> activeSessions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActiveSessionInfo {

        @Schema(required = true, description = "id of the refresh token associated with the session")
        @NonNull
        private Long refreshTokenId;

        @Schema(description = "user-agent associated with the session")
        private String userAgent;

        @Schema(required = true, description = "session creation timestamp (ISO-8601 format)")
        @NonNull
        private LocalDateTime createdAt;

        @Schema(required = true, description = "session latest usage timestamp (ISO-8601 format)")
        @NonNull
        private LocalDateTime lastUsedAt;
    }
}
