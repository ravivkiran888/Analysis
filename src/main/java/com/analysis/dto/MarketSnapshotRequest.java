package com.analysis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MarketSnapshotRequest {
    
    @JsonProperty("Exchange")
    private String exchange;
    
    @JsonProperty("ExchangeType")
    private String exchangeType;
    
    @JsonProperty("ScripCode")
    private Long scripCode;
    
    // Additional field for symbol (will be ignored in JSON serialization)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String symbol;
    
    // Constructor for easier creation
    public MarketSnapshotRequest(String exchange, String exchangeType, Integer scripCode, String symbol) {
        this.exchange = exchange;
        this.exchangeType = exchangeType;
        this.scripCode = scripCode != null ? scripCode.longValue() : null;
        this.symbol = symbol;
    }

    // ========== NESTED CLASSES FOR API REQUEST ==========
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApiRequest {
        private Head head;
        private Body body;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Head {
        private String key;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Body {
        private String clientCode;
        private List<MarketSnapshotRequest> data;
    }
}