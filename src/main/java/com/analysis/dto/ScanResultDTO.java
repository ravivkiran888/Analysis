package com.analysis.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.analysis.SignalState;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class ScanResultDTO {

	 @JsonIgnore
    private String scripCode;
    private String symbol;
    private SignalState signalState;

    private BigDecimal close;
    private BigDecimal vwap;
    @JsonIgnore
    private BigDecimal volume;

    private BigDecimal ema20;
    private BigDecimal ema50;

    @JsonIgnore
    private BigDecimal lastVolume;    
    @JsonIgnore
    private BigDecimal avgVolume20;   

    private BigDecimal volumeRatio;
    private String trend;

    @JsonProperty("lastVolume")
    private String lastVolumeFormatted;
    @JsonProperty("avgVolume")
    private String avgVolume20Formatted;
    
    
    @JsonProperty("currentVolume")
    private String currentVolume;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant updatedAt;
    
    private String sector = "UNKNOWN";

    public String getSector() {
        return sector == null ? "UNKNOWN" : sector;
    }
}
