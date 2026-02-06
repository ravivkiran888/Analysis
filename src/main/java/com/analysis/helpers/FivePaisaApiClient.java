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

    // Renamed method for clarity
    public String fetch5MinCandles(int scripCode, String fromDate, String toDate) {
        // This will automatically pace requests to 15 per second
        APPConstants.RATE_LIMITER.acquire();

        String accessToken = accessTokenService.getAccessToken();
        
        // Changed interval from 30m to 5m
        String url = String.format(
            "https://openapi.5paisa.com/V2/historical/N/C/%d/5m?from=%s&end=%s",
            scripCode, fromDate, toDate
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        log.debug("Fetching 5m candles for scrip {} from {} to {}", scripCode, fromDate, toDate);

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
        } catch (Exception e) {
            log.error("Failed to fetch 5m candles for scrip {}: {}", scripCode, e.getMessage());
            throw e;
        }
    }
    
    // Keep old method if needed for backward compatibility
    @Deprecated
    public String fetch30MinCandles(int scripCode, String fromDate, String toDate) {
        String url = String.format(
            "https://openapi.5paisa.com/V2/historical/N/C/%d/30m?from=%s&end=%s",
            scripCode, fromDate, toDate
        );
        return fetchHistoricalData(scripCode, url);
    }
    
    private String fetchHistoricalData(int scripCode, String url) {
        APPConstants.RATE_LIMITER.acquire();
        String accessToken = accessTokenService.getAccessToken();
        
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), String.class
            );
            return response.getBody();
        } catch (HttpClientErrorException e) {
            log.error("HTTP error for scrip {}: {}", scripCode, e.getStatusCode());
            throw e;
        }
    }
}