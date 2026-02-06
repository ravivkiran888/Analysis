package com.analysis.documents;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "market_snapshots")
public class MarketSnapshot {
    
    @Id
    private String id;
    
    @Indexed(unique = true)
    @Field("ScripCode")
    private String scripCode;
    
    @Field("Symbol")
    private String symbol;
    
    // Main price fields
    private Double lastTradedPrice;
    private Double netChange;
    private Double pClose;
    private Double open;
    private Double high;
    private Double low;
    private Double close;
    
    // Volume and quantity fields
    private Double volume;
    private Double totalBuyQuantity;
    private Double totalSellQuantity;
    private Integer buyQuantity;
    private Integer sellQuantity;
    private Integer lastQuantity;
    
    // Additional price levels
    private Double aHigh;
    private Double aLow;
    private Double lowerCircuitLimit;
    private Double upperCircuitLimit;
    
    // Market data
    private Double averageTradePrice;
    private Double openInterest;
    private Integer prevOpenInterest;
    private String marketCapital;
    private String exposureCategory;
    
    // Exchange info
    private String exchange;
    private String exchangeType;
    
    // Time fields
    private String lastTradeTime;
    private Instant capturedAt;
    private Instant createdAt;
    private Instant updatedAt;
}