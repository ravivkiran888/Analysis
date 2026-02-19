package com.analysis.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BuySignalDTO {
    private String symbol;
    private double ltp;
    private String marketBias;
    private double pressureRatio;
    private boolean breakoutSignal;
    private int supportStrength;
    private int resistanceStrength;
    private double atmPcr;
    private Double supportPrice;
    private Double supportDistancePercent;
    private Double resistancePrice;
    private Double resistanceDistancePercent;
}