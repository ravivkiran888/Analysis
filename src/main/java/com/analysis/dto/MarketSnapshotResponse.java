package com.analysis.dto;  // OR com.analysis.responses

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MarketSnapshotResponse {
    
    @JsonProperty("head")
    private Head head;
    
    @JsonProperty("body")
    private Body body;
    
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Head {
        @JsonProperty("responseCode")
        private String responseCode;
        
        @JsonProperty("status")
        private String status;
        
        @JsonProperty("statusDescription")
        private String statusDescription;
    }
    
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Body {
        @JsonProperty("CacheTime")
        private Integer cacheTime;
        
        @JsonProperty("Data")
        private List<MarketSnapshotData> data;
        
        @JsonProperty("Message")
        private String message;
        
        @JsonProperty("Status")
        private Integer status;
        
        @JsonProperty("TimeStamp")
        private String timeStamp;
    }
    
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class MarketSnapshotData {
        @JsonProperty("AHigh")
        private String aHigh;
        
        @JsonProperty("ALow")
        private String aLow;
        
        @JsonProperty("AverageTradePrice")
        private Double averageTradePrice;
        
        @JsonProperty("BuyQuantity")
        private Integer buyQuantity;
        
        @JsonProperty("Exchange")
        private String exchange;
        
        @JsonProperty("ExchangeType")
        private String exchangeType;
        
        @JsonProperty("ExposureCategory")
        private String exposureCategory;
        
        @JsonProperty("High")
        private String high;
        
        @JsonProperty("LastQuantity")
        private Integer lastQuantity;
        
        @JsonProperty("LastTradeTime")
        private String lastTradeTime;
        
        @JsonProperty("LastTradedPrice")
        private String lastTradedPrice;
        
        @JsonProperty("Low")
        private String low;
        
        @JsonProperty("LowerCircuitLimit")
        private String lowerCircuitLimit;
        
        @JsonProperty("MarketCapital")
        private String marketCapital;
        
        @JsonProperty("NetChange")
        private String netChange;
        
        @JsonProperty("Open")
        private String open;
        
        @JsonProperty("OpenInterest")
        private Double openInterest;
        
        @JsonProperty("PClose")
        private String pClose;
        
        @JsonProperty("PrevOpenInterest")
        private Integer prevOpenInterest;
        
        @JsonProperty("ScripCode")
        private Long scripCode;
        
        @JsonProperty("SellQuantity")
        private Integer sellQuantity;
        
        @JsonProperty("TotalBuyQuantity")
        private Double totalBuyQuantity;
        
        @JsonProperty("TotalSellQuantity")
        private Double totalSellQuantity;
        
        @JsonProperty("UpperCircuitLimit")
        private String upperCircuitLimit;
        
        @JsonProperty("Volume")
        private String volume;
    }
}