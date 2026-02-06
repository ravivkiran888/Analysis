package com.analysis.dto;

import java.math.BigDecimal;

public class Resistances {
    private BigDecimal r1;
    private BigDecimal r2;
    private BigDecimal r3;
    
    public Resistances(BigDecimal r1, BigDecimal r2, BigDecimal r3) {
        this.r1 = r1;
        this.r2 = r2;
        this.r3 = r3;
    }
    
    // Getters and Setters
    public BigDecimal getR1() { return r1; }
    public void setR1(BigDecimal r1) { this.r1 = r1; }
    
    public BigDecimal getR2() { return r2; }
    public void setR2(BigDecimal r2) { this.r2 = r2; }
    
    public BigDecimal getR3() { return r3; }
    public void setR3(BigDecimal r3) { this.r3 = r3; }
}
