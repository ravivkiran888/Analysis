package com.analysis.dto;

import java.math.BigDecimal;

public class BhavcopyLatestView {

    private String scripCode;
    private String symbol;
    private BigDecimal opnPric;
    private Long ttlTradgVol;

    public String getScripCode() {
        return scripCode;
    }

    public void setScripCode(String scripCode) {
        this.scripCode = scripCode;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public BigDecimal getOpnPric() {
        return opnPric;
    }

    public void setOpnPric(BigDecimal opnPric) {
        this.opnPric = opnPric;
    }

    public Long getTtlTradgVol() {
        return ttlTradgVol;
    }

    public void setTtlTradgVol(Long ttlTradgVol) {
        this.ttlTradgVol = ttlTradgVol;
    }
}
