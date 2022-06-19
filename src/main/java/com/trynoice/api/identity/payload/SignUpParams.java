package com.trynoice.api.identity.payload;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * A data transfer object to hold the body of sign-up requests.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SignUpParams {

    @Schema(required = true, description = "email address of the user")
    @NotBlank
    @Email
    @Size(min = 3, max = 64)
    private String email;

    @Schema(required = true, description = "name of the user")
    @NotBlank
    @Size(min = 1, max = 64)
    private String name;
}
