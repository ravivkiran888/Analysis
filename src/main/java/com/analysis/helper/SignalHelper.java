package com.analysis.helper;

import java.math.BigDecimal;
import com.analysis.dto.ScanResultDTO;

public class SignalHelper {

    public static void computeDerivedFields(ScanResultDTO dto) {
        BigDecimal lastVolume = safeParse(dto.getLastVolume());
        BigDecimal avgVolume20 = safeParse(dto.getAvgVolume20());
        BigDecimal ema20 = safeParse(dto.getEma20());
        BigDecimal ema50 = safeParse(dto.getEma50());

        // volumeRatio
        if (avgVolume20.compareTo(BigDecimal.ZERO) != 0) {
            dto.setVolumeRatio(lastVolume.divide(avgVolume20, 4, BigDecimal.ROUND_HALF_UP));
        } else {
            dto.setVolumeRatio(BigDecimal.ZERO);
        }

        // trend
        dto.setTrend(ema20.compareTo(ema50) > 0 ? "BULLISH" : "BEARISH");
    }

    private static BigDecimal safeParse(Object value) {
        try {
            if (value == null) return BigDecimal.ZERO;
            if (value instanceof BigDecimal) return (BigDecimal) value;
            if (value instanceof Number) return BigDecimal.valueOf(((Number) value).doubleValue());
            String str = value.toString().trim();
            if (str.isEmpty()) return BigDecimal.ZERO;
            return new BigDecimal(str);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}
