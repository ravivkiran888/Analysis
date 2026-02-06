package com.analysis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MarketSnapshotPayload {
    
    @JsonProperty("head")
    private Head head;
    
    @JsonProperty("body")
    private Body body;
    
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Head {
        @JsonProperty("Key")
        private String key;
    }
    
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Body {
        @JsonProperty("ClientCode")
        private String clientCode;
        
        @JsonProperty("Data")
        private List<MarketSnapshotRequest> data;
    }
}