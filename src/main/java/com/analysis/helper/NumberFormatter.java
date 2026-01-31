package com.analysis.helper;

import java.math.BigDecimal;

public class NumberFormatter {

    public static String formatLargeNumber(BigDecimal number) {
        if (number == null) return null;

        double value = number.doubleValue();
        if (value >= 1_000_000_000) return String.format("%.2fB", value / 1_000_000_000);
        if (value >= 1_000_000)     return String.format("%.2fM", value / 1_000_000);
        if (value >= 1_000)         return String.format("%.2fK", value / 1_000);

        return String.format("%.2f", value);
    }
}
