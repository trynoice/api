package com.trynoice.api.subscription;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trynoice.api.subscription.viewmodels.SubscriptionPlanResponse;
import com.trynoice.api.testing.AuthTestUtils;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.stream.Stream;

import static com.trynoice.api.testing.AuthTestUtils.createAccessToken;
import static com.trynoice.api.testing.AuthTestUtils.createAuthUser;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class SubscriptionControllerTest {

    @Value("${app.auth.hmac-secret}")
    private String hmacSecret;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MockMvc mockMvc;

    private String accessToken;

    @BeforeEach
    void setUp() {
        accessToken = createAccessToken(hmacSecret, createAuthUser(entityManager), AuthTestUtils.JwtType.VALID);
    }

    @ParameterizedTest(name = "{displayName} - provider={0} responseStatus={1}")
    @MethodSource("getPlansTestCases")
    void getPlans(String provider, int expectedResponseStatus) throws Exception {
        val request = get("/v1/subscriptions/plans")
            .header("Authorization", "bearer " + accessToken);

        if (provider != null) {
            request.queryParam("provider", provider);
        }

        val result = mockMvc.perform(request)
            .andExpect(status().is(expectedResponseStatus))
            .andReturn();

        if (expectedResponseStatus == HttpStatus.OK.value()) {
            val plans = objectMapper.readValue(
                result.getResponse().getContentAsByteArray(),
                SubscriptionPlanResponse[].class);

            assertNotEquals(0, plans.length);
            if (provider != null) {
                for (val plan : plans) {
                    assertEquals(provider, plan.getProvider());
                }
            }
        }
    }

    static Stream<Arguments> getPlansTestCases() {
        return Stream.of(
            arguments(null, HttpStatus.OK.value()),
            arguments("GOOGLE_PLAY", HttpStatus.OK.value()),
            arguments("RAZORPAY", HttpStatus.OK.value()),
            arguments("UNSUPPORTED_PROVIDER", HttpStatus.UNPROCESSABLE_ENTITY.value())
        );
    }
}
