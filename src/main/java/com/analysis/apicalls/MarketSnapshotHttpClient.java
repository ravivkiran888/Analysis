package com.analysis.apicalls;

import java.time.Duration;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.analysis.dto.MarketSnapshotPayload;
import com.analysis.dto.MarketSnapshotRequest;
import com.analysis.dto.MarketSnapshotResponse;
import com.analysis.service.AccessTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class MarketSnapshotHttpClient {

    private final WebClient webClient;
    private final AccessTokenService accessTokenService;
    private final ObjectMapper objectMapper;

    // Your working API key - keep this as is!
    private static final String API_KEY = "FrFcMUqe2TTkv1JiM7Q7IYodapckR8dH";
    private static final String CLIENT_CODE = "50407824";
    private static final String URL = "https://Openapi.5paisa.com/VendorsAPI/Service1.svc/v1/MarketSnapshot";

    public MarketSnapshotHttpClient(
            WebClient webClient,
            AccessTokenService accessTokenService,
            ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.accessTokenService = accessTokenService;
        this.objectMapper = objectMapper;
    }

    public MarketSnapshotResponse fetchSnapshot(List<MarketSnapshotRequest> requests) {
        try {
            // Get Bearer token
            String token = accessTokenService.getAccessToken();
            if (token == null || token.isEmpty()) {
                log.error("Access token is null or empty");
                return null;
            }

            // Build payload with hardcoded API key (exactly like your working code)
            MarketSnapshotPayload payload = new MarketSnapshotPayload(
                new MarketSnapshotPayload.Head(API_KEY),  // Hardcoded API key!
                new MarketSnapshotPayload.Body(CLIENT_CODE, requests)
            );

            log.info("Making MarketSnapshot API call for {} scrips", requests.size());

            // Log request for debugging (optional)
            if (log.isDebugEnabled()) {
                String requestJson = objectMapper.writeValueAsString(payload);
                log.debug("Request payload: {}", requestJson);
            }

            long startTime = System.currentTimeMillis();

            MarketSnapshotResponse response = webClient.post()
                .uri(URL)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token) // Bearer token in header
                .bodyValue(payload)
                .retrieve()
                .onStatus(
                    status -> status.is4xxClientError() || status.is5xxServerError(),
                    r -> r.bodyToMono(String.class)
                          .flatMap(errorBody -> {
                              log.error("API Error Response: Status={}, Body={}", 
                                  r.statusCode(), errorBody);
                              return Mono.error(new RuntimeException(
                                  String.format("API error %s: %s", r.statusCode(), errorBody)
                              ));
                          })
                )
                .bodyToMono(MarketSnapshotResponse.class)
                .timeout(Duration.ofSeconds(30))
                .block();

            long duration = System.currentTimeMillis() - startTime;

            if (response != null) {
                if (response.getBody() != null && 
                    response.getBody().getData() != null && 
                    !response.getBody().getData().isEmpty()) {
                    log.info("✅ Received data for {}/{} scrips in {} ms",
                        response.getBody().getData().size(), requests.size(), duration);
                } else {
                    log.warn("⚠️ Response with no data. Status: {}, Message: {}",
                        response.getBody() != null ? response.getBody().getStatus() : "N/A",
                        response.getBody() != null ? response.getBody().getMessage() : "N/A");
                }
            } else {
                log.error("Null response received from API");
            }

            return response;

        } catch (Exception e) {
            log.error("Error in fetchSnapshot: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Fetch snapshot for a single scrip
     */
    public MarketSnapshotResponse fetchSingleSnapshot(Integer scripCode, String symbol) {
        MarketSnapshotRequest request = new MarketSnapshotRequest("N", "C", scripCode, symbol);
        return fetchSnapshot(List.of(request));
    }

    /**
     * Check if response contains valid data
     */
    public boolean hasValidData(MarketSnapshotResponse response) {
        return response != null &&
               response.getBody() != null &&
               response.getBody().getData() != null &&
               !response.getBody().getData().isEmpty() &&
               response.getBody().getStatus() == 0;
    }

    /**
     * Extract data for a specific scrip code
     */
    public MarketSnapshotResponse.MarketSnapshotData getDataForScrip(
            MarketSnapshotResponse response, 
            Long scripCode) {
        if (!hasValidData(response)) {
            return null;
        }
        return response.getBody().getData().stream()
            .filter(data -> data.getScripCode().equals(scripCode))
            .findFirst()
            .orElse(null);
    }
}