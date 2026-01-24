package com.analysis.helpers;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.analysis.APPConstants;
import com.analysis.services.AccessTokenService;

@Component
public class FivePaisaApiClient {

    private final RestTemplate restTemplate;
    private final AccessTokenService accessTokenService;

    public FivePaisaApiClient(
            RestTemplateBuilder builder,
            AccessTokenService accessTokenService) {

        this.restTemplate = builder.build();
        this.accessTokenService = accessTokenService;
    }

    public String fetch30MinCandles(
            int scripCode,
            String fromDate,
            String toDate) {

        APPConstants.RATE_LIMITER.acquire();

        String accessToken = accessTokenService.getAccessToken();

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

        ResponseEntity<String> response =
                restTemplate.exchange(
                        url,
                        HttpMethod.GET,
                        entity,
                        String.class
                );

        return response.getBody();
    }
}
