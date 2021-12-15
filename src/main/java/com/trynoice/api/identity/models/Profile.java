package com.trynoice.api.identity.models;

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

    @NonNull
    private Long accountId;

    @NonNull
    private String name;

    @NonNull
    private String email;

    @NonNull
    private List<ActiveSessionInfo> activeSessions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActiveSessionInfo {

        @NonNull
        private Long refreshTokenId;

        private String userAgent;

        @NonNull
        private LocalDateTime createdAt, lastUsedAt;
    }
}
