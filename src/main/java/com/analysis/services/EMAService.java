package com.analysis.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.analysis.APPConstants;
import com.analysis.helpers.EMACalculator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class EMAService {

    private static final String INTERVAL = "30m";
    private static final String COLLECTION = "ema_30m";

    private static final int EMA_FAST = 20;
    private static final int EMA_SLOW = 50;
    private static final int VOLUME_LOOKBACK = 20;
    private static final BigDecimal VOLUME_MULTIPLIER =
            BigDecimal.valueOf(1.5);

    private final MongoTemplate mongoTemplate;
    private final ObjectMapper objectMapper;

    public EMAService(MongoTemplate mongoTemplate, ObjectMapper objectMapper) {
        this.mongoTemplate = mongoTemplate;
        this.objectMapper = objectMapper;
    }

    public void processApiResponse(
            String scripCode,
            String symbol,
            String json
    ) throws Exception {

        JsonNode candles =
                objectMapper.readTree(json)
                            .path("data")
                            .path("candles");

        if (candles.size() < EMA_SLOW) {
            return; // not enough candles
        }

        BigDecimal ema20 = null;
        BigDecimal ema50 = null;

        BigDecimal lastVolume = null;
        BigDecimal avgVolume20 = null;

        LocalDateTime lastCandleTime = null;

        for (int i = 0; i < candles.size(); i++) {

            JsonNode c = candles.get(i);

            lastCandleTime =
                    LocalDateTime.parse(c.get(0).asText());

            BigDecimal close =
                    c.get(4).decimalValue();

            BigDecimal volume =
                    c.get(5).decimalValue();

            lastVolume = volume;

            if (i == EMA_FAST - 1) {
                ema20 = sma(candles, EMA_FAST, i);
            }

            if (i == EMA_SLOW - 1) {
                ema50 = sma(candles, EMA_SLOW, i);
            }

            if (ema20 != null) {
                ema20 = EMACalculator.calculate(close, ema20, EMA_FAST);
            }

            if (ema50 != null) {
                ema50 = EMACalculator.calculate(close, ema50, EMA_SLOW);
            }

            if (i >= VOLUME_LOOKBACK - 1) {
                avgVolume20 = avgVolume(candles, VOLUME_LOOKBACK, i);
            }
        }

        if (ema20 == null || ema50 == null || avgVolume20 == null) {
            return;
        }

        boolean volumeExpansion =
                lastVolume.compareTo(
                        avgVolume20.multiply(VOLUME_MULTIPLIER)
                ) >= 0;

        upsertLatestEma(
                scripCode,
                symbol,
                ema20,
                ema50,
                lastVolume,
                avgVolume20,
                volumeExpansion,
                lastCandleTime
        );
    }

    /* ================== HELPERS ================== */

    private BigDecimal sma(JsonNode candles, int period, int endIndex) {
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = endIndex - period + 1; i <= endIndex; i++) {
            sum = sum.add(
                    candles.get(i).get(4).decimalValue()
            );
        }
        return sum.divide(
                BigDecimal.valueOf(period),
                4,
                RoundingMode.HALF_UP
        );
    }

    private BigDecimal avgVolume(
            JsonNode candles,
            int period,
            int endIndex
    ) {
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = endIndex - period + 1; i <= endIndex; i++) {
            sum = sum.add(
                    candles.get(i).get(5).decimalValue()
            );
        }
        return sum.divide(
                BigDecimal.valueOf(period),
                2,
                RoundingMode.HALF_UP
        );
    }

    /* ================== MONGO UPSERT ================== */

    private void upsertLatestEma(
            String scripCode,
            String symbol,
            BigDecimal ema20,
            BigDecimal ema50,
            BigDecimal lastVolume,
            BigDecimal avgVolume20,
            boolean volumeExpansion,
            LocalDateTime candleTime
    ) {

        Query query = new Query(
                Criteria.where(APPConstants.SCRIPT_CODE)
                        .is(scripCode)
        );

        Update update = new Update()
                .set("ema20", ema20)
                .set("ema50", ema50)
                .set("lastVolume", lastVolume)
                .set("avgVolume20", avgVolume20)
                .set("volumeExpansion", volumeExpansion)
                .set("lastCandleTime", candleTime)
                .set("updatedAt", Instant.now())
                .setOnInsert(APPConstants.SCRIPT_CODE, scripCode)
                .setOnInsert(APPConstants.SYMBOL, symbol)
                .setOnInsert("interval", INTERVAL);

        mongoTemplate.upsert(query, update, COLLECTION);
    }
}
