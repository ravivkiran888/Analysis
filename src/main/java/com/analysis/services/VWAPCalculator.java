package com.analysis.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.Deque;

import org.springframework.stereotype.Component;

import com.analysis.dto.ScripInfo;
import com.analysis.services.impl.RSIServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class VWAPCalculator {

    private static final int VOLUME_LOOKBACK = 10;

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

        BigDecimal cumulativeTPV = BigDecimal.ZERO;
        BigDecimal cumulativeVolume = BigDecimal.ZERO;
        BigDecimal previousVWAP = null;

        Deque<BigDecimal> recentVolumes = new ArrayDeque<>();

        BigDecimal lastClose = null;
        BigDecimal currentVWAP = null;
        BigDecimal vwapSlope = BigDecimal.ZERO;
        BigDecimal volumeExpansion = BigDecimal.ZERO;

        for (int i = 0; i < candles.size(); i++) {

            JsonNode c = candles.get(i);

            // [ time, open, high, low, close, volume ]
            BigDecimal high   = c.get(2).decimalValue();
            BigDecimal low    = c.get(3).decimalValue();
            BigDecimal close  = c.get(4).decimalValue();
            BigDecimal volume = c.get(5).decimalValue();

            lastClose = close;

            // Typical price
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

            if (cumulativeVolume.signum() > 0) {

                currentVWAP =
                        cumulativeTPV.divide(
                                cumulativeVolume,
                                4,
                                RoundingMode.HALF_UP);

                if (previousVWAP != null) {
                    vwapSlope = currentVWAP.subtract(previousVWAP);
                }

                previousVWAP = currentVWAP;
            }

            // Rolling volume window
            recentVolumes.add(volume);
            if (recentVolumes.size() > VOLUME_LOOKBACK) {
                recentVolumes.pollFirst();
            }

            if (recentVolumes.size() == VOLUME_LOOKBACK) {

                BigDecimal avgVolume = recentVolumes.stream()
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(VOLUME_LOOKBACK),
                                4,
                                RoundingMode.HALF_UP);

                if (avgVolume.signum() > 0) {
                    volumeExpansion =
                            volume.divide(avgVolume,
                                    4,
                                    RoundingMode.HALF_UP);
                }
            }
        }

        if (currentVWAP == null) {
            return;
        }

        ScripInfo info = scripCache.getScripInfo(scripCode);

        vwapService.saveOrUpdateVWAP(
                String.valueOf(scripCode),
                info.getSymbol(),
                currentVWAP,
                lastClose,
                cumulativeVolume,   // cumulative session volume
                info.getSector(),
                vwapSlope,
                volumeExpansion
        );

        // Pass same candle set to RSI (synchronized)
        rsiService.calculateAndSaveRSI(
                String.valueOf(scripCode),
                info.getSymbol(),
                candles
        );
    }
}
