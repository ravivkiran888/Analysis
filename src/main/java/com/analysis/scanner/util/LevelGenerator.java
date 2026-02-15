package com.analysis.scanner.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import com.analysis.model.CandleData;

public class LevelGenerator {

    /**
     * Generates support/resistance levels with trading insights from intraday candles.
     *
     * @param candles  List of 5-minute candles (each containing high, low, close, volume)
     * @return        Formatted string with levels and comments
     */
    public static String generateLevels(List<CandleData> candles) {
        if (candles == null || candles.isEmpty()) {
            return "No data available.";
        }

        // Find day high, low, and closing price
        BigDecimal dayHigh = candles.stream().map(CandleData::getHigh).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal dayLow = candles.stream().map(CandleData::getLow).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal close = candles.get(candles.size() - 1).getClose();

        // Calculate pivot points
        BigDecimal pivot = dayHigh.add(dayLow).add(close)
                .divide(new BigDecimal("3"), 2, RoundingMode.HALF_UP);

        BigDecimal r1 = pivot.multiply(new BigDecimal("2"))
                .subtract(dayLow).setScale(2, RoundingMode.HALF_UP);
        BigDecimal r2 = pivot.add(dayHigh.subtract(dayLow))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal s1 = pivot.multiply(new BigDecimal("2"))
                .subtract(dayHigh).setScale(2, RoundingMode.HALF_UP);
        BigDecimal s2 = pivot.subtract(dayHigh.subtract(dayLow))
                .setScale(2, RoundingMode.HALF_UP);

        // Identify key round numbers (e.g., 82.00) if near pivot levels
        BigDecimal roundResistance = new BigDecimal("82.00"); // Could be made dynamic
        BigDecimal roundSupport = new BigDecimal("81.40");   // Based on late sell-off low
        BigDecimal consolSupport = new BigDecimal("80.80"); // Previous consolidation area

        // Build output string
        StringBuilder sb = new StringBuilder();
        sb.append("📊 **Key Levels for Tomorrow**\n\n");

        // Resistance levels
        sb.append("**Resistance 1:** ").append(r1).append(" – Break with volume → buy\n");
        sb.append("**Resistance 2:** ").append(dayHigh).append(" – Day high – profit booking zone\n");
        if (roundResistance.compareTo(r1) > 0 && roundResistance.compareTo(dayHigh) < 0) {
            sb.append("**Resistance (round):** ").append(roundResistance)
              .append(" – Psychological level; watch for reaction\n");
        }

        // Support levels
        sb.append("**Support 1:** ").append(s1).append(" – Below this → further downside\n");
        sb.append("**Support 2:** ").append(s2).append(" – Previous consolidation – strong support\n");
        if (roundSupport.compareTo(s1) < 0 && roundSupport.compareTo(s2) > 0) {
            sb.append("**Support (recent low):** ").append(roundSupport)
              .append(" – Late sell-off low; breakdown would weaken structure\n");
        }
        if (consolSupport.compareTo(s2) > 0 && consolSupport.compareTo(s1) < 0) {
            sb.append("**Support (consolidation):** ").append(consolSupport)
              .append(" – Base before rally; holds above this is bullish\n");
        }

        // Volume insight
        sb.append("\n📈 **Volume Insight:**\n");
        long lastHourVolume = candles.stream()
                .filter(c -> c.getTimestamp().getHour() >= 14) // after 2 PM
                .mapToLong(CandleData::getVolume).sum();
        if (lastHourVolume > 1_000_000) {
            sb.append("– Heavy volume in last hour (selling pressure). Cautious on longs near close.\n");
        } else {
            sb.append("– Volume tapered in last hour; typical end-of-day activity.\n");
        }

        return sb.toString();
    }
}