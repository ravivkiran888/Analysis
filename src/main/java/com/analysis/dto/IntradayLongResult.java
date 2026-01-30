package com.analysis.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class IntradayLongResult {

    private String symbol;
    private double price;
    private double vwap;
    private double previousDayHigh;
}
