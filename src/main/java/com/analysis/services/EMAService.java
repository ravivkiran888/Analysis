package com.analysis.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(EMAService.class);

	
	private static final String INTERVAL = "5m";  // Changed from 30m to 5m
    private static final String COLLECTION = "ema_5m";  // Changed collection name

    // Adjusted EMA periods for 5m timeframe (more responsive)
    private static final int EMA_FAST = 20;      // 20 periods = 100 minutes
    private static final int EMA_SLOW = 50;      // 50 periods = 250 minutes (~4 hours)
    
    // Volume lookback adjusted for higher frequency
    private static final int VOLUME_LOOKBACK = 20;  // Last 20 periods = 100 minutes
    
    // Slightly higher multiplier for 5m (more noise in smaller timeframes)
    private static final BigDecimal VOLUME_MULTIPLIER = BigDecimal.valueOf(2.0);

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

        // Need more candles for EMA calculations since we're using shorter timeframe
        int minCandlesNeeded = Math.max(EMA_SLOW, VOLUME_LOOKBACK) + 10;
        if (candles.size() < minCandlesNeeded) {
            log.warn("Not enough candles for {} ({}). Need: {}, Have: {}", 
                    symbol, scripCode, minCandlesNeeded, candles.size());
            return;
        }

        BigDecimal ema20 = null;
        BigDecimal ema50 = null;

        BigDecimal lastVolume = null;
        BigDecimal avgVolume20 = null;

        LocalDateTime lastCandleTime = null;
        
        // Use last 2-3 days of data for 5m candles
        int startIdx = Math.max(0, candles.size() - 300); // ~300 candles = ~25 hours
        
        for (int i = startIdx; i < candles.size(); i++) {

            JsonNode c = candles.get(i);

            lastCandleTime =
                    LocalDateTime.parse(c.get(0).asText());

            BigDecimal close =
                    c.get(4).decimalValue();

            BigDecimal volume =
                    c.get(5).decimalValue();

            lastVolume = volume;

            // Initialize EMA with SMA when we have enough data
            if (i == startIdx + EMA_FAST - 1) {
                ema20 = sma(candles, EMA_FAST, i, startIdx);
            }

            if (i == startIdx + EMA_SLOW - 1) {
                ema50 = sma(candles, EMA_SLOW, i, startIdx);
            }

            if (ema20 != null) {
                ema20 = EMACalculator.calculate(close, ema20, EMA_FAST);
            }

            if (ema50 != null) {
                ema50 = EMACalculator.calculate(close, ema50, EMA_SLOW);
            }

            if (i >= startIdx + VOLUME_LOOKBACK - 1) {
                avgVolume20 = avgVolume(candles, VOLUME_LOOKBACK, i, startIdx);
            }
        }

        if (ema20 == null || ema50 == null || avgVolume20 == null) {
            log.warn("EMA calculation incomplete for {} ({})", symbol, scripCode);
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
        
        log.debug("Updated 5m EMA for {}: EMA20={}, EMA50={}, VolExp={}", 
                symbol, ema20.setScale(2, RoundingMode.HALF_UP), 
                ema50.setScale(2, RoundingMode.HALF_UP), volumeExpansion);
    }

    /* ================== HELPERS ================== */

    private BigDecimal sma(JsonNode candles, int period, int endIndex, int startIdx) {
        BigDecimal sum = BigDecimal.ZERO;
        int actualStart = Math.max(startIdx, endIndex - period + 1);
        
        for (int i = actualStart; i <= endIndex; i++) {
            sum = sum.add(
                    candles.get(i).get(4).decimalValue()
            );
        }
        
        int count = endIndex - actualStart + 1;
        if (count < period) {
            log.warn("Incomplete SMA calculation: need {}, have {}", period, count);
        }
        
        return sum.divide(
                BigDecimal.valueOf(count),
                4,
                RoundingMode.HALF_UP
        );
    }

    private BigDecimal avgVolume(
            JsonNode candles,
            int period,
            int endIndex,
            int startIdx
    ) {
        BigDecimal sum = BigDecimal.ZERO;
        int actualStart = Math.max(startIdx, endIndex - period + 1);
        
        for (int i = actualStart; i <= endIndex; i++) {
            sum = sum.add(
                    candles.get(i).get(5).decimalValue()
            );
        }
        
        int count = endIndex - actualStart + 1;
        return sum.divide(
                BigDecimal.valueOf(count),
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