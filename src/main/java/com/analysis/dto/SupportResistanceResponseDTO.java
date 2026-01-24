package com.analysis.dto;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class SupportResistanceResponseDTO {

    private String symbol;
    private List<SupportResistanceLevelDTO> supports;
    private List<SupportResistanceLevelDTO> resistances;

    public SupportResistanceResponseDTO(
            String symbol,
            List<SupportResistanceLevelDTO> supports,
            List<SupportResistanceLevelDTO> resistances) {

        this.symbol = symbol;
        this.supports = supports;
        this.resistances = resistances;
    }


}
