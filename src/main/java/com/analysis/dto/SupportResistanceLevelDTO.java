package com.analysis.dto;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class SupportResistanceLevelDTO {

    private String type;     // SUPPORT / RESISTANCE
    private String source;   // VWAP / PDH / PDL / PDC
    private BigDecimal from;
    private BigDecimal to;
    private int strength;

    public SupportResistanceLevelDTO(
            String type,
            String source,
            BigDecimal from,
            BigDecimal to,
            int strength) {

        this.type = type;
        this.source = source;
        this.from = from;
        this.to = to;
        this.strength = strength;
    }


}
