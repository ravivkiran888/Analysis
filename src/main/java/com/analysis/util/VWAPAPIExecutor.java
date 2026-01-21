package com.analysis.util;

import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.analysis.APPConstants;
import com.analysis.exceptionm.TooManyRequestsException;
import com.analysis.requests.VWAPRequest;
import com.analysis.services.VWAPCalculator;

@Component
public class VWAPAPIExecutor {

	  @Value("${fivepaisa.access.token}")
	  private String accessToken;

	
    private static final Logger log =
            LoggerFactory.getLogger(VWAPAPIExecutor.class);

    private final WebClient webClient;
    private final VWAPCalculator vwapCalculator;

    public VWAPAPIExecutor(
            WebClient webClient,
            VWAPCalculator vwapCalculator) {

        this.webClient = webClient;
        this.vwapCalculator = vwapCalculator;
    }

    public void execute(List<VWAPRequest> requests) {

        for (VWAPRequest request : requests) {

            // GLOBAL rate control
            APPConstants.RATE_LIMITER.acquire();

            try {
                callApi(request);

            } catch (Exception ex) {
                log.error(
                    "VWAP API failed | scrip={}",
                    request.getScripCode(),
                    ex
                );
            }
        }
    }

    private void callApi(VWAPRequest request) {

    	
    	
    	
        try {
            String response =
                    webClient.get()
                            .uri(request.getUrl())
                            .headers(h -> {
                                h.setBearerAuth(accessToken);
                                h.setContentType(
                                        MediaType.APPLICATION_JSON);
                            })
                            .retrieve()
                            .bodyToMono(String.class)
                            .timeout(Duration.ofSeconds(10))
                            .block();

            try {
				vwapCalculator.calculateFromApiResponse(
				        request.getScripCode(),
				        response
				);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

        } catch (WebClientResponseException.TooManyRequests ex) {
        	throw new TooManyRequestsException();
        }
    }

}
