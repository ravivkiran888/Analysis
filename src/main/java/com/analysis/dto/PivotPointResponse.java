package com.analysis.dto;

import java.math.BigDecimal;

public class PivotPointResponse {
    private String symbol;
    private String date;
    private BigDecimal pivot;
    private Resistances resistances;
    private Supports supports;
    
    // Constructor
    public PivotPointResponse(String symbol, String date, BigDecimal pivot, 
                            Resistances resistances, Supports supports) {
        this.symbol = symbol;
        this.date = date;
        this.pivot = pivot;
        this.resistances = resistances;
        this.supports = supports;
    }
    
    // Getters and Setters
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    
    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    
    public BigDecimal getPivot() { return pivot; }
    public void setPivot(BigDecimal pivot) { this.pivot = pivot; }
    
    public Resistances getResistances() { return resistances; }
    public void setResistances(Resistances resistances) { this.resistances = resistances; }
    
    public Supports getSupports() { return supports; }
    public void setSupports(Supports supports) { this.supports = supports; }
}
