package com.analysis.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OptionChainIndicators {

    private String symbol;
    
    @JsonProperty("currentPrice")
    private double underlying;
    
    @JsonProperty("atm")
    private int atmStrike;

    private int support;
    private int resistance;

    private int atmCallOI;
    private int atmPutOI;

    private int atmCallVolume;
    private int atmPutVolume;

    private int atmTotalVolume;

    private Instant updatedAt;
}