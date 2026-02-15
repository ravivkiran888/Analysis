package com.analysis.scanner.util;

import com.analysis.model.CandleData;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Component
public class TradingCalculator {

    // ===================== VWAP =====================
    public BigDecimal calculateVWAP(List<CandleData> candles) {
        BigDecimal cumulativeTPV = BigDecimal.ZERO;
        BigDecimal cumulativeVolume = BigDecimal.ZERO;

        for (CandleData candle : candles) {
            BigDecimal typicalPrice = candle.getHigh()
                    .add(candle.getLow())
                    .add(candle.getClose())
                    .divide(BigDecimal.valueOf(3), 6, RoundingMode.HALF_UP);

            BigDecimal volume = BigDecimal.valueOf(candle.getVolume());
            cumulativeTPV = cumulativeTPV.add(typicalPrice.multiply(volume));
            cumulativeVolume = cumulativeVolume.add(volume);
        }

        if (cumulativeVolume.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        return cumulativeTPV.divide(cumulativeVolume, 4, RoundingMode.HALF_UP);
    }

    // ===================== VWAP Slope (using last 3 points) =====================
    public BigDecimal calculateVWAPSlope(List<CandleData> candles) {
        if (candles.size() < 30) {
            return BigDecimal.ZERO;
        }

        List<BigDecimal> vwaps = new ArrayList<>();
        int[] points = {candles.size() - 30, candles.size() - 20, candles.size() - 10};

        for (int endIdx : points) {
            BigDecimal cumulativeTPV = BigDecimal.ZERO;
            BigDecimal cumulativeVolume = BigDecimal.ZERO;

            for (int i = 0; i <= endIdx; i++) {
                CandleData candle = candles.get(i);
                BigDecimal typicalPrice = candle.getHigh()
                        .add(candle.getLow())
                        .add(candle.getClose())
                        .divide(BigDecimal.valueOf(3), 6, RoundingMode.HALF_UP);

                BigDecimal volume = BigDecimal.valueOf(candle.getVolume());
                cumulativeTPV = cumulativeTPV.add(typicalPrice.multiply(volume));
                cumulativeVolume = cumulativeVolume.add(volume);
            }

            if (cumulativeVolume.compareTo(BigDecimal.ZERO) > 0) {
                vwaps.add(cumulativeTPV.divide(cumulativeVolume, 4, RoundingMode.HALF_UP));
            }
        }

        if (vwaps.size() == 3) {
            // Slope = (last - first) / 2 (since 3 points over ~20 candles)
            return vwaps.get(2).subtract(vwaps.get(0))
                    .divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }

    // ===================== EMA =====================
    public BigDecimal calculateEMA(List<CandleData> candles, int period) {
        if (candles.size() < period) {
            return null;
        }

        List<BigDecimal> prices = candles.stream()
                .map(CandleData::getClose)
                .toList();

        // Calculate initial SMA
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = prices.size() - period; i < prices.size(); i++) {
            sum = sum.add(prices.get(i));
        }
        BigDecimal ema = sum.divide(BigDecimal.valueOf(period), 4, RoundingMode.HALF_UP);

        // Multiplier: 2 / (period + 1)
        BigDecimal multiplier = BigDecimal.valueOf(2)
                .divide(BigDecimal.valueOf(period + 1), 8, RoundingMode.HALF_UP);

        // Calculate EMA for remaining periods
        for (int i = prices.size() - period + 1; i < prices.size(); i++) {
            ema = prices.get(i).subtract(ema)
                    .multiply(multiplier)
                    .add(ema);
        }

        return ema.setScale(4, RoundingMode.HALF_UP);
    }

    // ===================== RSI (14 period) =====================
    public BigDecimal calculateRSI(List<CandleData> candles, int period) {
        if (candles.size() < period + 1) {
            return BigDecimal.valueOf(50); // Neutral when insufficient data
        }

        BigDecimal gain = BigDecimal.ZERO;
        BigDecimal loss = BigDecimal.ZERO;

        for (int i = candles.size() - period; i < candles.size(); i++) {
            BigDecimal change = candles.get(i).getClose()
                    .subtract(candles.get(i - 1).getClose());

            if (change.compareTo(BigDecimal.ZERO) > 0) {
                gain = gain.add(change);
            } else {
                loss = loss.add(change.abs());
            }
        }

        BigDecimal avgGain = gain.divide(BigDecimal.valueOf(period), 4, RoundingMode.HALF_UP);
        BigDecimal avgLoss = loss.divide(BigDecimal.valueOf(period), 4, RoundingMode.HALF_UP);

        if (avgLoss.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.valueOf(100);
        }

        BigDecimal rs = avgGain.divide(avgLoss, 4, RoundingMode.HALF_UP);
        return BigDecimal.valueOf(100)
                .subtract(BigDecimal.valueOf(100)
                        .divide(BigDecimal.ONE.add(rs), 2, RoundingMode.HALF_UP));
    }

    // ===================== Volume Expansion (current vs avg of previous N) =====================
    public BigDecimal calculateVolumeExpansion(List<CandleData> candles, int lookback) {
        if (candles.size() < lookback + 1) {
            return BigDecimal.ONE; // Neutral when insufficient data
        }

        long currentVolume = candles.get(candles.size() - 1).getVolume();

        BigDecimal sum = BigDecimal.ZERO;
        for (int i = candles.size() - lookback - 1; i < candles.size() - 1; i++) {
            sum = sum.add(BigDecimal.valueOf(candles.get(i).getVolume()));
        }

        BigDecimal avgVolume = sum.divide(BigDecimal.valueOf(lookback), 2, RoundingMode.HALF_UP);

        if (avgVolume.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ONE;
        }

        return BigDecimal.valueOf(currentVolume)
                .divide(avgVolume, 2, RoundingMode.HALF_UP);
    }
}