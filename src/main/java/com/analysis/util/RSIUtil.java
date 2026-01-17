package com.analysis.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public final class RSIUtil {

    private static final int PERIOD = 14;

    private RSIUtil() {}

    /**
     * Calculates RSI-14 using Wilder's smoothing method.
     * Requires at least 15 close prices (period + 1).
     */
    public static BigDecimal calculateRSI14(List<BigDecimal> closes) {

        if (closes == null || closes.size() < PERIOD + 1) {
            return null;
        }

        BigDecimal gainSum = BigDecimal.ZERO;
        BigDecimal lossSum = BigDecimal.ZERO;

        // Step 1: Initial average gain/loss
        for (int i = 1; i <= PERIOD; i++) {
            BigDecimal diff = closes.get(i).subtract(closes.get(i - 1));

            if (diff.signum() > 0) {
                gainSum = gainSum.add(diff);
            } else {
                lossSum = lossSum.add(diff.abs());
            }
        }

        BigDecimal avgGain =
                gainSum.divide(BigDecimal.valueOf(PERIOD), 6, RoundingMode.HALF_UP);

        BigDecimal avgLoss =
                lossSum.divide(BigDecimal.valueOf(PERIOD), 6, RoundingMode.HALF_UP);

        // Step 2: Wilder smoothing for remaining values (if any)
        for (int i = PERIOD + 1; i < closes.size(); i++) {

            BigDecimal diff = closes.get(i).subtract(closes.get(i - 1));

            BigDecimal gain = diff.signum() > 0 ? diff : BigDecimal.ZERO;
            BigDecimal loss = diff.signum() < 0 ? diff.abs() : BigDecimal.ZERO;

            avgGain =
                    avgGain.multiply(BigDecimal.valueOf(PERIOD - 1))
                           .add(gain)
                           .divide(BigDecimal.valueOf(PERIOD), 6, RoundingMode.HALF_UP);

            avgLoss =
                    avgLoss.multiply(BigDecimal.valueOf(PERIOD - 1))
                           .add(loss)
                           .divide(BigDecimal.valueOf(PERIOD), 6, RoundingMode.HALF_UP);
        }

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
                                        RoundingMode.HALF_UP
                                )
                );
    }
}
