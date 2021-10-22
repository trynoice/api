package com.trynoice.api.identity.models;

import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;

/**
 * A data transfer object to hold the body of sign-in requests.
 */
@Data
public class SignInRequest {

    @NotBlank
    @Email
    private String email;
}
