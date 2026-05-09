package com.analysis.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Service;

import com.analysis.dto.PivotLevels;

@Service
public class PivotCalculatorService {

    public PivotLevels calculateLevels(
            String prevHigh,
            String prevLow,
            String prevClose,
            String symbol
    ) {

        double high = Double.parseDouble(prevHigh);
        double low = Double.parseDouble(prevLow);
        double close = Double.parseDouble(prevClose);

        // Pivot Point
        double pp = (high + low + close) / 3;

        // Resistance
        double r1 = (2 * pp) - low;
        double r2 = pp + (high - low);
        double r3 = high + 2 * (pp - low);

        // Support
        double s1 = (2 * pp) - high;
        double s2 = pp - (high - low);
        double s3 = low - 2 * (high - pp);

        PivotLevels levels = new PivotLevels();

        levels.setPivotPoint(format(pp));

        levels.setR1(format(r1));
        levels.setR2(format(r2));
        levels.setR3(format(r3));

        levels.setS1(format(s1));
        levels.setS2(format(s2));
        levels.setS3(format(s3));

        levels.setSymbol(symbol);
        
        return levels;
    }

    private String format(double value) {

        return BigDecimal.valueOf(value)
                .setScale(2, RoundingMode.HALF_UP)
                .toPlainString();
    }
}