package com.analysis.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import com.analysis.APPConstants;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient fivePaisaWebClient() {
        return WebClient.builder()
                .baseUrl(APPConstants.BASE_URL)
                .build();
    }
}
