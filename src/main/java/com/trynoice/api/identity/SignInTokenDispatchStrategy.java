package com.trynoice.api.identity;

import com.trynoice.api.identity.exceptions.SignInTokenDispatchException;
import lombok.NonNull;
import lombok.val;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.Message;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SesException;

/**
 * {@link SignInTokenDispatchStrategy} is an abstraction layer to control how sign-in tokens (or
 * links) are delivered. There are two implementations available for {@link
 * SignInTokenDispatchStrategy}: {@link Console Console} and {@link Email Email}.
 */
public interface SignInTokenDispatchStrategy {

    /**
     * Dispatches the sign-in token to be delivered at the specified destination address.
     *
     * @param token       sign-in token to deliver.
     * @param destination destination address as accepted by the strategy implementation used, e.g.
     *                    an email address if {@link Email} is used.
     * @throws SignInTokenDispatchException if the implementation fails to dispatch the token.
     */
    void dispatch(@NonNull String token, String destination) throws SignInTokenDispatchException;


    /**
     * {@link Console} simply prints the sign-in tokens to the standard output.
     */
    class Console implements SignInTokenDispatchStrategy {

        @Override
        public void dispatch(@NonNull String token, @NonNull String destination) {
            System.out.println();
            System.out.printf("sign in token to retrieve auth credentials for %s: %s%n", destination, token);
            System.out.println();
        }
    }

    /**
     * {@link Email} uses AWS Simple Email Service to deliver sign-in tokens to the specified
     * destination addresses.
     */
    class Email implements SignInTokenDispatchStrategy {

        private final String sourceEmail;
        private final SesClient sesClient;

        public Email(String sourceEmail) {
            this(sourceEmail, SesClient.create());
        }

        Email(@NonNull String sourceEmail, @NonNull SesClient sesClient) {
            this.sourceEmail = sourceEmail;
            this.sesClient = sesClient;
        }

        @Override
        public void dispatch(@NonNull String token, String destination) throws SignInTokenDispatchException {
            val dest = Destination.builder().toAddresses(destination).build();
            val body = Body.builder()
                .text(
                    buildUtf8Content(
                        // TODO: add a proper email template
                        "You are one click away from signing into Noice. Please open the " +
                            "following link using the Noice Android app or the browser to " +
                            "complete the process.\n\n" + token))
                .build();

            val request = SendEmailRequest.builder()
                .source(sourceEmail)
                .destination(dest)
                .message(
                    Message.builder()
                        .subject(buildUtf8Content("Noice: Sign in"))
                        .body(body)
                        .build())
                .build();

            try {
                sesClient.sendEmail(request);
            } catch (SesException e) {
                throw new SignInTokenDispatchException("failed to send sign-in link to user", e);
            }
        }

        @NonNull
        private Content buildUtf8Content(@NonNull String content) {
            return Content.builder()
                .charset("utf-8")
                .data(content)
                .build();
        }
    }
}
