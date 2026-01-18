package com.analysis.services.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Service;

import com.analysis.model.RSIValue;
import com.analysis.repository.RSIValueRepository;
import com.fasterxml.jackson.databind.JsonNode;

@Service
public class RSIServiceImpl {

    private static final int PERIOD = 14;

    private final RSIValueRepository repository;

    public RSIServiceImpl(RSIValueRepository repository) {
        this.repository = repository;
    }

    /**
     * TradingView-style RSI(14)
     * Calculated ONLY from candle response
     */
    public void calculateAndSaveRSI(
            int scripCode,
            String symbol,
            JsonNode candles) {

        // Need at least 15 candles (14 diffs)
        if (candles == null || candles.size() < PERIOD + 1) {
            return;
        }
        
       
        BigDecimal gainSum = BigDecimal.ZERO;
        BigDecimal lossSum = BigDecimal.ZERO;

        BigDecimal prevClose =
                candles.get(0).get(4).decimalValue();

        for (int i = 1; i <= PERIOD; i++) {

            BigDecimal close =
                    candles.get(i).get(4).decimalValue();

            BigDecimal diff = close.subtract(prevClose);

            if (diff.signum() > 0) {
                gainSum = gainSum.add(diff);
            } else {
                lossSum = lossSum.add(diff.abs());
            }

            prevClose = close;
        }

        BigDecimal avgGain =
                gainSum.divide(
                        BigDecimal.valueOf(PERIOD),
                        6,
                        RoundingMode.HALF_UP);

        BigDecimal avgLoss =
                lossSum.divide(
                        BigDecimal.valueOf(PERIOD),
                        6,
                        RoundingMode.HALF_UP);

        BigDecimal rsi = calculateRSI(avgGain, avgLoss);

        repository.deleteByScripCode(scripCode);
                

        
        repository.save(
                new RSIValue(
                        scripCode,
                        symbol,
                        PERIOD,
                        rsi
                )
        );
    }

    private BigDecimal calculateRSI(
            BigDecimal avgGain,
            BigDecimal avgLoss) {

        if (avgLoss.signum() == 0) {
            return BigDecimal.valueOf(100);
        }

        BigDecimal rs =
                avgGain.divide(avgLoss, 6, RoundingMode.HALF_UP);

        return BigDecimal.valueOf(100)
                .subtract(
                        BigDecimal.valueOf(100)
                                .divide(
                                        BigDecimal.ONE.add(rs),
                                        2,
                                        RoundingMode.HALF_UP));
    }
}
