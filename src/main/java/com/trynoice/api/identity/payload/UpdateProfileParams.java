package com.trynoice.api.identity.payload;

import com.trynoice.api.platform.validation.annotations.NullOrNotBlank;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A data transfer object to hold the body of update profile requests.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileParams {

    @Schema(description = "updated email address of the user")
    @NullOrNotBlank
    @Email
    @Size(min = 3, max = 64)
    private String email;

    @Schema(description = "updated name of the user")
    @NullOrNotBlank
    @Size(min = 1, max = 64)
    private String name;
}
