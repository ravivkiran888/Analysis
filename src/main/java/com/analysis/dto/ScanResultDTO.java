package com.analysis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScanResultDTO {
    
    // From signal_states
    private String scripCode;
    private String symbol;
    private String signalState;
    private String sector;
    
    
    private Instant evaluatedAt;
    
    // From market_snapshots
    private Double open;
    private Double high;
    private Double low;
    private Double close;
    private Long volume;
    private Double netChange;
    private Double lastTradedPrice;
}