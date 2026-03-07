package com.analysis.documents;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.List;

@Data
@NoArgsConstructor
@Document(collection = "stock_levels")
public class StockLevelsDocument {
   
    private String symbol;
    private double ltp;
    private String marketBias;
    private double pressureRatio;
    private boolean breakoutSignal;
    private int supportStrength;
    private int resistanceStrength;
    private double atmPcr;

    @Field("active_support")
    private ActiveLevel activeSupport;

    @Field("active_resistance")
    private ActiveLevel activeResistance;

    // Optional: zones if needed
    private List<Zone> supportZones;
    private List<Zone> resistanceZones;
    
    @Field("description")
    private String description;
 

    @Data
    @NoArgsConstructor
    public static class ActiveLevel {
        private double price;
        @Field("distance_percent")
        private double distancePercent;
        private long oi;
        @Field("oi_change")
        private long oiChange;
    }

    @Data
    @NoArgsConstructor
    public static class Zone {
        private double lower;
        private double upper;
        @Field("total_oi")
        private long totalOi;
        private double strength;
    }
}