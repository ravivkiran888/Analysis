package com.analysis.apicalls;


import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.analysis.constants.Constants;
import com.analysis.service.AccessTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class APIClient {

    private final WebClient webClient;
    private final AccessTokenService accessTokenService;
   
    private static final int FIVE_MIN_CANDLES_PER_DAY = 75;

    public APIClient(WebClient webClient, AccessTokenService accessTokenService, ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.accessTokenService = accessTokenService;
    }

   
    public String getHistoricalData(int scripCode, String interval, int candleCount) throws Exception {
        // Apply rate limiter (from your constants)
        Constants.RATE_LIMITER.acquire();

        // Calculate date range
        LocalDate endDate = LocalDate.now(); // or last trading day; could be adjusted
        // Estimate how many days of data to fetch
        int daysToFetch = estimateDaysNeeded(interval, candleCount);
        LocalDate startDate = endDate.minusDays(daysToFetch);

        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE;
        String from = startDate.format(formatter);
        String end = endDate.format(formatter);

        String url = String.format("%s/V2/historical/N/C/%d/%s?from=%s&end=%s",
                Constants.BASE_URL, scripCode, interval, from, end);
        
        System.out.println(url);

        String accessToken = accessTokenService.getAccessToken();

        try {
        	
        	if(accessToken!=null)
        	{
            return fetchWithToken(url, accessToken);
        	}
        	else
        	{
        		return null;
        	}
        } catch (WebClientResponseException.Unauthorized e) {
        	
        	return null;
        }
    }

    private String fetchWithToken(String url, String token) {
        return webClient.get()
                .uri(url)
                .headers(headers -> {
                    headers.setBearerAuth(token);
                    headers.setContentType(MediaType.APPLICATION_JSON);
                })
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(10))
                .block();
    }

    /**
     * Estimate number of calendar days needed to get at least `candleCount` candles.
     * For 5‑min candles, a full trading day gives 75 candles; we add a buffer.
     */
    private int estimateDaysNeeded(String interval, int candleCount) {
        if ("5m".equals(interval)) {
            // Trading day has ~75 candles; we add 2 extra days for weekends/holidays
            return (candleCount / FIVE_MIN_CANDLES_PER_DAY) + 2;
        }
        // For other intervals, you can add logic or default to a safe value
        return 5; // default fallback
    }
}