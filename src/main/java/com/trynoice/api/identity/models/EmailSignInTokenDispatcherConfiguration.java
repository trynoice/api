package com.trynoice.api.identity.models;

import lombok.Data;
import lombok.SneakyThrows;
import lombok.val;
import org.springframework.util.ResourceUtils;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import java.nio.file.Files;

/**
 * Configuration properties required by the {@link
 * com.trynoice.api.identity.SignInTokenDispatchStrategy.Email}.
 */
@Data
public class EmailSignInTokenDispatcherConfiguration {

    /**
     * Used as source address when sending sign-in emails.
     */
    @NotBlank
    @Email
    private String fromEmail;

    /**
     * Subject line for the sign-in emails.
     */
    @NotBlank
    private String subject;

    /**
     * Template of the sign-in email body.
     */
    @NotBlank
    private String template;

    /**
     * Template of the sign-in link in the email body.
     */
    @NotBlank
    private String linkFmt;

    /**
     * A support email to include in sign-in email body.
     */
    @NotBlank
    @Email
    private String supportEmail;

    @SneakyThrows
    public void setTemplate(String template) {
        val file = ResourceUtils.getFile(template);
        // remove extra indentation spaces.
        this.template = Files.readString(file.toPath()).replaceAll("\\s+", " ");
    }
}
