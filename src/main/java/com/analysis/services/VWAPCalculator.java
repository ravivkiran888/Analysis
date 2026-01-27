package com.analysis.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import com.analysis.services.impl.RSIServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class VWAPCalculator {

    private static final int INTERVAL_MINUTES = 5;

    // Reject last candle if volume < 70% of previous
    private static final BigDecimal LAST_CANDLE_VOLUME_RATIO =
            BigDecimal.valueOf(0.70);

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ScripCache scripCache;
    private final VWAPService vwapService;
    private final RSIServiceImpl rsiService;

    public VWAPCalculator(
            ScripCache scripCache,
            VWAPService vwapService,
            RSIServiceImpl rsiService) {

        this.scripCache = scripCache;
        this.vwapService = vwapService;
        this.rsiService = rsiService;
    }

    /**
     * Entry point called by scheduler
     */
    public void calculateFromApiResponse(
            int scripCode,
            String json) throws Exception {

        JsonNode candles =
                objectMapper.readTree(json)
                            .path("data")
                            .path("candles");

        if (candles == null || candles.isEmpty()) {
            return;
        }
        
        BigDecimal lastClose = null;
        BigDecimal totalTradedVolume = null;


        BigDecimal cumulativeTPV = BigDecimal.ZERO;
        BigDecimal cumulativeVolume = BigDecimal.ZERO;

        for (int i = 0; i < candles.size(); i++) {

            if (!isCompleteCandle(candles, i)) {
                continue;
            }

            JsonNode c = candles.get(i);

            // candle format:
            // [ time, open, high, low, close, volume ]
            BigDecimal high   = c.get(2).decimalValue();
            BigDecimal low    = c.get(3).decimalValue();
            BigDecimal close  = c.get(4).decimalValue();
            BigDecimal volume = c.get(5).decimalValue();
            
            lastClose = close; 
            totalTradedVolume = volume;


            BigDecimal typicalPrice =
                    high.add(low).add(close)
                        .divide(BigDecimal.valueOf(3),
                                6,
                                RoundingMode.HALF_UP);

            cumulativeTPV =
                    cumulativeTPV.add(
                            typicalPrice.multiply(volume));

            cumulativeVolume =
                    cumulativeVolume.add(volume);
        }

        String symbol = scripCache.getSymbol(scripCode);

        // ---- VWAP ----
        if (cumulativeVolume.signum() > 0) {

            BigDecimal vwap =
                    cumulativeTPV.divide(
                            cumulativeVolume,
                            2,
                            RoundingMode.HALF_UP);

            
            
            vwapService.saveOrUpdateVWAP(
                    String.valueOf(scripCode),
                    symbol,
                    vwap,
                    lastClose,
                    totalTradedVolume
            );
        }

        // ---- RSI (TradingView-style) ----
        rsiService.calculateAndSaveRSI(
        		String.valueOf(scripCode),
                symbol,
                candles
        );
    }

    /**
     * Candle is complete if:
     * - Next candle exists exactly +5 minutes
     * - OR (last candle) volume is >= 70% of previous
     */
    private boolean isCompleteCandle(
            JsonNode candles,
            int index) {

        // Last candle
        if (index == candles.size() - 1) {
            return isLastVolumeValid(candles, index);
        }

        LocalDateTime t1 =
                LocalDateTime.parse(
                        candles.get(index)
                               .get(0)
                               .asText());

        LocalDateTime t2 =
                LocalDateTime.parse(
                        candles.get(index + 1)
                               .get(0)
                               .asText());

        return Duration.between(t1, t2)
                       .toMinutes() == INTERVAL_MINUTES;
    }

    /**
     * Validate partial last candle using volume ratio
     */
    private boolean isLastVolumeValid(
            JsonNode candles,
            int index) {

        if (index == 0) {
            return false;
        }

        BigDecimal lastVolume =
                candles.get(index)
                       .get(5)
                       .decimalValue();

        BigDecimal prevVolume =
                candles.get(index - 1)
                       .get(5)
                       .decimalValue();

        if (prevVolume.signum() == 0) {
            return false;
        }

        BigDecimal ratio =
                lastVolume.divide(
                        prevVolume,
                        4,
                        RoundingMode.HALF_UP);

        return ratio.compareTo(
                LAST_CANDLE_VOLUME_RATIO) >= 0;
    }
}
