package com.analysis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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
}