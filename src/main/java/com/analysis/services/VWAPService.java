package com.analysis.services;

import java.math.BigDecimal;

public interface VWAPService {

    void deleteAllVWAP();

    void saveOrUpdateVWAP(
            int scripCode,
            String symbol,
            BigDecimal vwap
    );
}
