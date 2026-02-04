package com.analysis.helpers;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

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
import com.analysis.services.AccessTokenService;

@Component
public class FivePaisaApiClient {

	private static final Logger log = LoggerFactory.getLogger(FivePaisaApiClient.class);

	
    private final RestTemplate restTemplate;
    private final AccessTokenService accessTokenService;

    public FivePaisaApiClient(RestTemplateBuilder builder,
                              AccessTokenService accessTokenService) {
        this.restTemplate = builder
            .setConnectTimeout(Duration.ofSeconds(10))
            .setReadTimeout(Duration.ofSeconds(30))
            .build();
        this.accessTokenService = accessTokenService;
    }

    public String fetch30MinCandles(int scripCode, String fromDate, String toDate) {
        // This will automatically pace requests to 15 per second
        APPConstants.RATE_LIMITER.acquire();

        String accessToken = accessTokenService.getAccessToken();
        
        String url = String.format(
            "https://openapi.5paisa.com/V2/historical/N/C/%d/30m?from=%s&end=%s",
            scripCode, fromDate, toDate
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), String.class
            );
            return response.getBody();
            
        } catch (HttpClientErrorException.TooManyRequests e) {
            log.error("Rate limit exceeded for scrip {}", scripCode);
            throw e;
        } catch (HttpClientErrorException.Unauthorized e) {
            log.error("Token expired for scrip {}", scripCode);
            throw e;
        }
    }
}