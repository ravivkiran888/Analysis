package com.analysis.dto;

import java.math.BigDecimal;

public class Supports {
    private BigDecimal s1;
    private BigDecimal s2;
    private BigDecimal s3;
    
    public Supports(BigDecimal s1, BigDecimal s2, BigDecimal s3) {
        this.s1 = s1;
        this.s2 = s2;
        this.s3 = s3;
    }
    
    // Getters and Setters
    public BigDecimal getS1() { return s1; }
    public void setS1(BigDecimal s1) { this.s1 = s1; }
    
    public BigDecimal getS2() { return s2; }
    public void setS2(BigDecimal s2) { this.s2 = s2; }
    
    public BigDecimal getS3() { return s3; }
    public void setS3(BigDecimal s3) { this.s3 = s3; }
}