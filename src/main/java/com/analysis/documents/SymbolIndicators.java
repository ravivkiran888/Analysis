package com.analysis.documents;


import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.analysis.constants.Constants;

import lombok.Data;
@Data
@Document(collection = Constants.SYMBOL_INDICATORS_COLLECTION)
public class SymbolIndicators {
    @Id
    private String id;

    @Indexed(unique = true)
    private String scripCode;               // Unique identifier for the symbol

    private String symbol;                   // Human-readable symbol
    private Instant timestamp;                // When this scan was performed
    private int candleCount;                   // Number of candles used
    private String mode;                        // "EARLY" or "FULL"

    // Prices
    private BigDecimal price;                  // Latest close price
    private BigDecimal vwap;                    // VWAP
    private BigDecimal volumeExpansion;         // Volume ratio (current / avg)

    // Full scanner fields (null in early mode)
    private BigDecimal ema20;
    private BigDecimal ema50;
    private BigDecimal vwapSlope;
    private BigDecimal rsi;

    // Signal: "ENTRY_READY" or "WAIT"
    private String signal;
    
    
    
    // Market snapshot fields
    private BigDecimal dayHigh;
    private BigDecimal dayLow;
    private BigDecimal dayOpen;
    private BigDecimal lastTradedPrice;
    private BigDecimal dayChange;
    
    private Long totalDayVolume;  // Today's total volume from MarketSnapshot
    
    private String levelInsights;


    
}