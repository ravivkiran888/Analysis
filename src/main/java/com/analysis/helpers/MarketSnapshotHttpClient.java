package com.analysis.helpers;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.analysis.dto.MarketSnapshotPayload;
import com.analysis.dto.MarketSnapshotRequest;
import com.analysis.dto.MarketSnapshotResponse;
import com.analysis.services.AccessTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class MarketSnapshotHttpClient {

    private final WebClient webClient;
    private final AccessTokenService accessTokenService;
    private final ObjectMapper objectMapper;

    private static final String URL =
        "https://Openapi.5paisa.com/VendorsAPI/Service1.svc/v1/MarketSnapshot";

    public MarketSnapshotResponse fetchSnapshot(List<MarketSnapshotRequest> requests) {
        
        try {
            MarketSnapshotPayload payload = new MarketSnapshotPayload(
                new MarketSnapshotPayload.Head("FrFcMUqe2TTkv1JiM7Q7IYodapckR8dH"),
                new MarketSnapshotPayload.Body("50407824", requests)
            );

            // Log the request payload for debugging
 //           String requestJson = objectMapper.writeValueAsString(payload);
 
            String token = accessTokenService.getAccessToken();
 
            MarketSnapshotResponse response = webClient.post()
                .uri(URL)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .bodyValue(payload)
                .retrieve()
                .onStatus(
                    s -> s.isError(),
                    r -> r.bodyToMono(String.class)
                          .flatMap(b -> {
                              log.error("API Error Response: {}", b);
                              return Mono.error(new RuntimeException("API error: " + b));
                          })
                )
                .bodyToMono(MarketSnapshotResponse.class)
                .block();

            if (response != null) {
                log.debug("Response received successfully");
            } else {
                log.error("Null response received from API");
            }

            return response;
            
        } catch (Exception e) {
            log.error("Error in fetchSnapshot: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to fetch market snapshot", e);
        }
    }
}