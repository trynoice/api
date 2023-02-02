package com.trynoice.api.identity.payload;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A data transfer object to hold the body of sign-in requests.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SignInParams {

    @Schema(required = true, description = "email address of the user")
    @NotBlank
    @Email
    private String email;
}
