package com.analysis.apicalls;

import org.springframework.beans.factory.annotation.Value;
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
public class OptionsAPIClient {

    private final RestTemplate restTemplate;
    private final GrowAccessTokenService accessTokenService;
    private final String expiryDate;

    public OptionsAPIClient(RestTemplate restTemplate,
    		GrowAccessTokenService accessTokenService,
                            @Value("${expirydate}") String expiryDate) {
        this.restTemplate = restTemplate;
        this.accessTokenService = accessTokenService;
        this.expiryDate = expiryDate;
    }

    
    @CircuitBreaker(name = "growwOptionChain", fallbackMethod = "getOptionsChainFallback")
    public String getOptionsChain(String symbol) {

        Constants.RATE_LIMITER.acquire();

        String url = String.format(
                "https://api.groww.in/v1/option-chain/exchange/NSE/underlying/%s?expiry_date=%s",
                symbol, expiryDate
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + accessTokenService.getGrowAccessToken());
        headers.set("X-API-VERSION", "1.0");

        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, String.class
        );

        return response.getBody();
    }
    
    
	public String getOptionsChainFallback(String symbol, Throwable ex) {

	    log.error("Groww API failed for {}: {}", symbol, ex.getMessage());

		return null;
	}

   }