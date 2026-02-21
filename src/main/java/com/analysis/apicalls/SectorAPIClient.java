package com.analysis.apicalls;

import com.analysis.constants.Constants;
import com.analysis.service.GrowAccessTokenService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

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

    /**
     * Fetches sector data for a given trading symbol with retry on rate limit (429).
     */
    public String getSectorData(String tradingSymbol) {
        int maxRetries = 3;
        int retryCount = 0;
        long baseWaitMs = 1000; // 1 second initial backoff

        while (true) {
            try {
                // Acquire permit from rate limiter (blocks if needed)
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
                ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
                return response.getBody();

            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() == 429 && retryCount < maxRetries) {
                    retryCount++;
                    long waitTime = baseWaitMs * (1L << retryCount); // exponential: 2^retry * 1000 ms
                    log.warn("Rate limited for {} (attempt {}/{}). Retrying in {} ms",
                            tradingSymbol, retryCount, maxRetries, waitTime);
                    try {
                        Thread.sleep(waitTime);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Retry interrupted", ie);
                    }
                } else {
                    log.error("Failed to fetch sector data for {}: {} - {}",
                            tradingSymbol, e.getStatusCode(), e.getResponseBodyAsString());
                    throw e;
                }
            } catch (Exception e) {
                log.error("Unexpected error for {}: {}", tradingSymbol, e.getMessage());
                throw e;
            }
        }
    }
}