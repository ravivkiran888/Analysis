package com.analysis.util;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.analysis.requests.VWAPRequest;
import com.analysis.services.VWAPCalculator;
import com.google.common.util.concurrent.RateLimiter;

@Component
public class VWAPAPIExecutor {

	
	  @Value("${fivepaisa.access.token}")
	  private String accessToken;

	  
    private static final int MAX_RPS = 24;

    private final RateLimiter rateLimiter =
            RateLimiter.create(MAX_RPS);


    private final WebClient webClient;
    private final VWAPCalculator vwapCalculator;

    public VWAPAPIExecutor(WebClient fivePaisaWebClient,
                           VWAPCalculator vwapCalculator) {
        this.webClient = fivePaisaWebClient;
        this.vwapCalculator = vwapCalculator;
    }
    
    private <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            result.add(list.subList(
                    i,
                    Math.min(i + size, list.size())
            ));
        }
        return result;
    }


    public void executeInBatches(
            List<VWAPRequest> requests,
            int batchSize
    ) {

        List<List<VWAPRequest>> batches = partition(requests, batchSize);

        for (int i = 0; i < batches.size(); i++) {

            List<VWAPRequest> batch = batches.get(i);
            System.out.println(
                "Starting batch " + (i + 1)
                + "/" + batches.size()
                + " | size=" + batch.size()
            );

            for (VWAPRequest request : batch) {
                rateLimiter.acquire();
                callApi(request);
            }

            // sleep once after the batch
            try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

            // Optional cooling period
            
        }
    }

    // 
    
    private void callApi(VWAPRequest request) {

    	
    	System.out.println(request.getUrl());
    	
        try {
            String response =
                    webClient.get()
                            .uri(request.getUrl())
                            .headers(h -> {
                                h.setBearerAuth(accessToken);
                                h.setContentType(MediaType.APPLICATION_JSON);
                            })
                            .retrieve()
                            .bodyToMono(String.class)
                            .timeout(Duration.ofSeconds(10))
                            .block();

            vwapCalculator.calculateFromApiResponse(
                    request.getScripCode(),
                    response
            );

        } catch (Exception ex) {
            System.err.println(
                "API failed for ScripCode=" + request.getScripCode()
                + " | " + ex.getMessage()
            );
        }
    }

    
    
    }
