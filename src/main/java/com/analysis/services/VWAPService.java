package com.analysis.services;

import java.math.BigDecimal;

public interface VWAPService {

   

    void saveOrUpdateVWAP(
            String scripCode,
            String symbol,
            BigDecimal vwap,
            BigDecimal close,
            BigDecimal volume
    );
}
