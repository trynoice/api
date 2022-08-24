package com.trynoice.api.subscription.upstream;

import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.manyTimes;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ForeignExchangeRatesProviderTest {

    private MockRestServiceServer mockServer;
    private ForeignExchangeRatesProvider exchangeRatesProvider;

    @BeforeEach
    void setUp() {
        val restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.createServer(restTemplate);
        exchangeRatesProvider = new ForeignExchangeRatesProvider(restTemplate);
    }

    @Test
    void getRateForCurrency() {
        val xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<gesmes:Envelope>\n" +
            "<gesmes:subject>Reference rates</gesmes:subject>\n" +
            "<gesmes:Sender>\n" +
            "<gesmes:name>European Central Bank</gesmes:name>\n" +
            "</gesmes:Sender>\n" +
            "<Cube>\n" +
            "<Cube time=\"2022-06-28\">\n" +
            "<Cube currency=\"USD\" rate=\"10\"/>\n" +
            "<Cube currency=\"INR\" rate=\"100\"/>\n" +
            "</Cube>\n" +
            "</Cube>\n" +
            "</gesmes:Envelope>";

        mockServer.expect(manyTimes(), requestTo(ForeignExchangeRatesProvider.DAILY_RATES_URL))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(xml, MediaType.TEXT_XML));

        assertTrue(exchangeRatesProvider.getRateForCurrency("USD", "INR").isEmpty());
        assertDoesNotThrow(() -> exchangeRatesProvider.maybeUpdateRates());
        val rate = exchangeRatesProvider.getRateForCurrency("USD", "INR");
        assertTrue(rate.isPresent());
        assertEquals(10.0, rate.get());
    }
}
