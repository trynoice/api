package com.trynoice.api.identity.models;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;

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
