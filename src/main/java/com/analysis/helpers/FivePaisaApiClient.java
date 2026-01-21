package com.analysis.helpers;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import com.analysis.APPConstants;

@Component
public class FivePaisaApiClient {

    private final RestTemplate restTemplate;

    @Value("${fivepaisa.access.token}")
    private String accessToken;

    public FivePaisaApiClient(RestTemplateBuilder builder) {
        this.restTemplate = builder.build();
    }

    public String fetch30MinCandles(
            int scripCode,
            String fromDate,
            String toDate) {

        APPConstants.RATE_LIMITER.acquire(); // blocks safely

        String url = String.format(
            "https://openapi.5paisa.com/V2/historical/N/C/%d/30m?from=%s&end=%s",
            scripCode,
            fromDate,
            toDate
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response =
                    restTemplate.exchange(
                            url,
                            HttpMethod.GET,
                            entity,
                            String.class
                    );

            return response.getBody();

        } catch (HttpClientErrorException.TooManyRequests ex) {

            try {
                Thread.sleep(5000);
            } catch (InterruptedException ignored) {}

            throw ex;
        }
    }

}
