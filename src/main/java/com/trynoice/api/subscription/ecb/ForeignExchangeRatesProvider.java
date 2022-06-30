package com.trynoice.api.subscription.ecb;

import lombok.NonNull;
import lombok.val;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Provides the latest foreign exchange rates for a given currency using the European Central Bank
 * API. The data uses Euro (EUR) as its base currency.
 *
 * @see <a href="https://www.ecb.europa.eu/stats/policy_and_exchange_rates/euro_reference_exchange_rates/html/index.en.html">
 * Euro foreign exchange reference rates</a>
 */
public class ForeignExchangeRatesProvider {

    static final String DAILY_RATES_URL = "https://www.ecb.europa.eu/stats/eurofxref/eurofxref-daily.xml";

    private final RestTemplate restTemplate;
    private final DocumentBuilderFactory documentBuilderFactory;
    private final Map<String, Double> rates;
    private Long dailyRatesLastUpdatedAt;

    public ForeignExchangeRatesProvider(@NonNull RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        this.documentBuilderFactory = DocumentBuilderFactory.newDefaultInstance();
        this.rates = new HashMap<>();
    }

    /**
     * Queries and saves the daily exchange rate data from the ECB API.
     */
    public void maybeUpdateRates() {
        val headers = new HttpHeaders();
        if (dailyRatesLastUpdatedAt != null && !rates.isEmpty()) {
            headers.setIfModifiedSince(dailyRatesLastUpdatedAt);
        }

        val response = restTemplate.exchange(DAILY_RATES_URL, HttpMethod.GET, new HttpEntity<Void>(headers), String.class);
        if (response.getStatusCode() != HttpStatus.OK) {
            return;
        }

        dailyRatesLastUpdatedAt = response.getHeaders().getLastModified();
        if (response.getBody() == null) {
            return;
        }

        final Document doc;
        try {
            val builder = documentBuilderFactory.newDocumentBuilder();
            doc = builder.parse(new InputSource(new StringReader(response.getBody())));
        } catch (ParserConfigurationException | IOException | SAXException e) {
            // shouldn't happen normally.
            throw new RuntimeException(e);
        }

        doc.getDocumentElement().normalize();
        val cubes = doc.getElementsByTagName("Cube");

        synchronized (this) {
            this.rates.clear();
            rates.put("EUR", 1.0);
            for (int i = 0; i < cubes.getLength(); i++) {
                val cube = cubes.item(i);
                if (!cube.hasAttributes()) {
                    continue;
                }

                val attrs = cube.getAttributes();
                val currencyNode = attrs.getNamedItem("currency");
                val rateNode = attrs.getNamedItem("rate");
                if (currencyNode == null || rateNode == null) {
                    continue;
                }

                val currencyCode = currencyNode.getNodeValue();
                val rate = rateNode.getNodeValue();
                this.rates.put(currencyCode, Double.valueOf(rate));
            }
        }
    }

    /**
     * Returns the latest conversion rate from {@code fromCurrencyCode} to {@code toCurrencyCode}
     * with Euro as base currency for all conversions.
     *
     * @param fromCurrencyCode a not {@literal null} ISO 4217 currency code.
     * @param toCurrencyCode   a not {@literal null} ISO 4217 currency code.
     * @return an {@link Optional} of {@link Double} with the conversion rate; {@link
     * Optional#empty()} if the conversion is not possible due to missing exchange rate data.
     */
    @NonNull
    public synchronized Optional<Double> getRateForCurrency(@NonNull String fromCurrencyCode, @NonNull String toCurrencyCode) {
        if (!rates.containsKey(fromCurrencyCode) || !rates.containsKey(toCurrencyCode)) {
            return Optional.empty();
        }

        return Optional.of(rates.get(toCurrencyCode) / rates.get(fromCurrencyCode));
    }
}
