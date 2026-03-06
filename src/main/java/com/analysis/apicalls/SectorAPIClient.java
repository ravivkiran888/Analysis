package com.analysis.apicalls;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.analysis.constants.Constants;
import com.analysis.service.GrowAccessTokenService;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class SectorAPIClient {

    private final RestTemplate restTemplate;
    private final GrowAccessTokenService accessTokenService;

    public SectorAPIClient(RestTemplate restTemplate,
                           GrowAccessTokenService accessTokenService) {
        this.restTemplate = restTemplate;
        this.accessTokenService = accessTokenService;
    }

    
    
    @CircuitBreaker(name = "growwApi", fallbackMethod = "sectorFallback")
    public String getSectorData(String tradingSymbol) {

        Constants.RATE_LIMITER.acquire();

        String url = String.format(
                "https://api.groww.in/v1/live-data/quote?exchange=NSE&segment=CASH&trading_symbol=%s",
                tradingSymbol
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + accessTokenService.getGrowAccessToken());
        headers.set("X-API-VERSION", "1.0");

        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response =
                restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Groww API error: " + response.getStatusCode());
        }

        return response.getBody();
    }
    
    
    public String sectorFallback(String tradingSymbol, Throwable t) {
        log.error("Circuit breaker triggered for {} : {}", tradingSymbol, t.getMessage());
        return null;
    }
    
}