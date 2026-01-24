package com.analysis.dto;

import java.util.List;

public class SupportResistanceSimpleResponseDTO {

    private String symbol;
    private List<SupportResistanceLevelDTO> buyZones;
    private List<SupportResistanceLevelDTO> sellZones;

    public SupportResistanceSimpleResponseDTO(
            String symbol,
            List<SupportResistanceLevelDTO> buyZones,
            List<SupportResistanceLevelDTO> sellZones) {

        this.symbol = symbol;
        this.buyZones = buyZones;
        this.sellZones = sellZones;
    }

    public String getSymbol() {
        return symbol;
    }

    public List<SupportResistanceLevelDTO> getBuyZones() {
        return buyZones;
    }

    public List<SupportResistanceLevelDTO> getSellZones() {
        return sellZones;
    }
}
