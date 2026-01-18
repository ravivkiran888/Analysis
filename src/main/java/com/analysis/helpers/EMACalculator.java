package com.analysis.helpers;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class EMACalculator {

    public static BigDecimal calculate(
            BigDecimal close,
            BigDecimal previousEma,
            int period
    ) {

        BigDecimal multiplier =
                BigDecimal.valueOf(2.0 / (period + 1));

        return close.subtract(previousEma)
                .multiply(multiplier)
                .add(previousEma)
                .setScale(4, RoundingMode.HALF_UP);
    }
}
