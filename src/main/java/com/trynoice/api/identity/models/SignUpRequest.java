package com.trynoice.api.identity.models;

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
public class SignUpRequest {

    @NotBlank
    @Email
    @Size(min = 3, max = 64)
    private String email;

    @NotBlank
    @Size(min = 1, max = 64)
    private String name;
}
