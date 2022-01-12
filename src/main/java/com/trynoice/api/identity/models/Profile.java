package com.trynoice.api.identity.models;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

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
}
