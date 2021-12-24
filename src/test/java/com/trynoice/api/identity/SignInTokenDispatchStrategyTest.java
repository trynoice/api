package com.trynoice.api.identity;

import com.trynoice.api.identity.exceptions.SignInTokenDispatchException;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SendEmailResponse;
import software.amazon.awssdk.services.ses.model.SesException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SignInTokenDispatchStrategyTest {

    @Nested
    class EmailTest {

        @Mock
        private AuthConfiguration.EmailSignInTokenDispatcherConfiguration config;

        @Mock
        private SesClient sesClient;

        private SignInTokenDispatchStrategy.Email emailStrategy;

        @BeforeEach
        void setUp() {
            when(config.getFromEmail()).thenReturn("test-from-email");
            when(config.getSubject()).thenReturn("test-subject");
            when(config.getTemplate()).thenReturn("test-template");
            when(config.getLinkFmt()).thenReturn("test-link-fmt");
            when(config.getSupportEmail()).thenReturn("test-support-email");

            emailStrategy = new SignInTokenDispatchStrategy.Email(config, sesClient);
        }

        @Test
        void dispatch_withUpstreamError() {
            val token = "test-token";
            val destination = "destination";

            // when upstream service throws an error
            when(sesClient.sendEmail((SendEmailRequest) any()))
                .thenThrow(
                    SesException.builder()
                        .message("test-error")
                        .build());

            assertThrows(SignInTokenDispatchException.class, () ->
                emailStrategy.dispatch(token, destination));
        }

        @Test
        void dispatch_withoutUpstreamError() throws SignInTokenDispatchException {
            val destination = "test-destination";
            val token = "test-token";

            // when upstream service behaves normally
            when(sesClient.sendEmail((SendEmailRequest) any()))
                .thenReturn(SendEmailResponse.builder().build());

            emailStrategy.dispatch(token, destination);

            val requestCaptor = ArgumentCaptor.forClass(SendEmailRequest.class);
            verify(sesClient, times(1)).sendEmail(requestCaptor.capture());
            assertEquals(config.getFromEmail(), requestCaptor.getValue().source());
            assertEquals(destination, requestCaptor.getValue().destination().toAddresses().get(0));
        }
    }
}
