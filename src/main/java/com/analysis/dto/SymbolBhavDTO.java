package com.analysis.dto;

public class SymbolBhavDTO {

    private String symbol;
    private Double opnPric;
    private Long ttlTradgVol;
    private Double ttlTrfVal;

    public SymbolBhavDTO(String symbol, Double opnPric,
                         Long ttlTradgVol, Double ttlTrfVal) {
        this.symbol = symbol;
        this.opnPric = opnPric;
        this.ttlTradgVol = ttlTradgVol;
        this.ttlTrfVal = ttlTrfVal;
    }

    public String getSymbol() {
        return symbol;
    }

    public Double getOpnPric() {
        return opnPric;
    }

    public Long getTtlTradgVol() {
        return ttlTradgVol;
    }

    public Double getTtlTrfVal() {
        return ttlTrfVal;
    }
}
