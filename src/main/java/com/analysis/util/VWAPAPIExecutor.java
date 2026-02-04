package com.analysis.util;

import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.analysis.APPConstants;
import com.analysis.requests.VWAPRequest;
import com.analysis.services.AccessTokenService;
import com.analysis.services.VWAPCalculator;

@Component
public class VWAPAPIExecutor {

    private static final Logger log = LoggerFactory.getLogger(VWAPAPIExecutor.class);

    private final WebClient webClient;
    private final VWAPCalculator vwapCalculator;
    private final AccessTokenService accessTokenService;

    public VWAPAPIExecutor(
            WebClient webClient,
            VWAPCalculator vwapCalculator,
            AccessTokenService accessTokenService) {

        this.webClient = webClient;
        this.vwapCalculator = vwapCalculator;
        this.accessTokenService = accessTokenService;
    }

    public void execute(List<VWAPRequest> requests) {
        for (VWAPRequest request : requests) {
            // This will rate limit to 18 requests/sec
            APPConstants.RATE_LIMITER.acquire();
            
            try {
                callApiWithRetry(request, false);
                
            } catch (Exception ex) {
                log.error("VWAP API failed after retries | scrip={}", 
                        request.getScripCode(), ex);
            }
        }
    }

    private void callApiWithRetry(VWAPRequest request, boolean isRetry) {
        String accessToken = accessTokenService.getAccessToken();

        try {
            String response = webClient.get()
                    .uri(request.getUrl())
                    .headers(h -> {
                        h.setBearerAuth(accessToken);
                        h.setContentType(MediaType.APPLICATION_JSON);
                    })
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            // Process response
            vwapCalculator.calculateFromApiResponse(request.getScripCode(), response);
            
        } catch (WebClientResponseException.TooManyRequests ex) {
            if (!isRetry) {
                log.warn("Rate limit hit for scrip {}. Retrying in 2 seconds...", 
                        request.getScripCode());
                try {
                    Thread.sleep(2000);
                    callApiWithRetry(request, true); // Retry once
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted during retry", ie);
                }
            } else {
                log.error("Rate limit hit again for scrip {}. Giving up.", 
                        request.getScripCode());
            }
            
        } catch (Exception e) {
            if (!isRetry) {
                // First attempt failed, try once more
                try {
                    log.warn("First attempt failed for scrip {}. Retrying...", 
                            request.getScripCode());
                    Thread.sleep(1000);
                    callApiWithRetry(request, true);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            } else {
                throw new RuntimeException(e);
            }
        }
    }
}