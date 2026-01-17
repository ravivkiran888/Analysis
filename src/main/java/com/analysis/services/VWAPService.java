package com.analysis.services;

import java.math.BigDecimal;

public interface VWAPService {

    void deleteEntryInVWAP(int scripCode);

    void saveOrUpdateVWAP(
            int scripCode,
            String symbol,
            BigDecimal vwap
    );
}
