package com.analysis.dto;

import java.math.BigDecimal;
import com.analysis.SignalState;
import lombok.Data;

@Data

public class ScanResultDTO {

    private String symbol;
    private SignalState signalState;

    private BigDecimal close;
    private BigDecimal vwap;
    private BigDecimal volume;

    private String updatedAt;

   
}
