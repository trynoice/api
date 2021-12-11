package com.trynoice.api.subscription.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateSubscriptionParams {

    @NotNull
    @Min(1)
    private Short planId;

    @NotBlank
    private String successUrl;

    @NotBlank
    private String cancelUrl;
}
