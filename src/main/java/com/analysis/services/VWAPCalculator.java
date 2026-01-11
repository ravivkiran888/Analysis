package com.analysis.services;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Component;

import com.analysis.services.ScripCache;
import com.analysis.services.VWAPService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class VWAPCalculator {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ScripCache scripCache;
    private final VWAPService vwapService;

    public VWAPCalculator(
            ScripCache scripCache,
            VWAPService vwapService) {
        this.scripCache = scripCache;
        this.vwapService = vwapService;
    }

    public void calculateFromApiResponse(
            int scripCode,
            String jsonResponse) throws Exception {

        JsonNode root = objectMapper.readTree(jsonResponse);
        JsonNode candles = root.path("data").path("candles");

        BigDecimal cumulativeTPV = BigDecimal.ZERO;
        BigDecimal cumulativeVolume = BigDecimal.ZERO;

        for (JsonNode candle : candles) {

            BigDecimal high   = candle.get(2).decimalValue();
            BigDecimal low    = candle.get(3).decimalValue();
            BigDecimal close  = candle.get(4).decimalValue();
            BigDecimal volume = candle.get(5).decimalValue();

            BigDecimal typicalPrice =
                    high.add(low).add(close)
                        .divide(BigDecimal.valueOf(3), 6, RoundingMode.HALF_UP);

            cumulativeTPV =
                    cumulativeTPV.add(typicalPrice.multiply(volume));

            cumulativeVolume =
                    cumulativeVolume.add(volume);
        }

        if (cumulativeVolume.signum() == 0) {
            return;
        }

        BigDecimal vwap =
                cumulativeTPV.divide(
                        cumulativeVolume,
                        2,
                        RoundingMode.HALF_UP);

        String symbol = scripCache.getSymbol(scripCode);

        vwapService.saveOrUpdate(
                scripCode,
                symbol,
                vwap
        );
    }
}
