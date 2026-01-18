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

import com.analysis.helpers.EMACalculator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class EMAService {

    private static final String INTERVAL = "60m";
    private static final String COLLECTION = "ema_60m";

    private final MongoTemplate mongoTemplate;
    private final ObjectMapper objectMapper;

    public EMAService(MongoTemplate mongoTemplate, ObjectMapper objectMapper) {
        this.mongoTemplate = mongoTemplate;
        this.objectMapper = objectMapper;
    }

    public void processApiResponse(int scripCode, String json) throws Exception {

        JsonNode candles =
                objectMapper.readTree(json)
                            .path("data")
                            .path("candles");

        BigDecimal ema20 = null;
        BigDecimal ema50 = null;
        LocalDateTime lastCandleTime = null;

        for (int i = 0; i < candles.size(); i++) {

            JsonNode c = candles.get(i);

            lastCandleTime =
                    LocalDateTime.parse(c.get(0).asText());

            BigDecimal close =
                    c.get(4).decimalValue();

            if (i == 19) ema20 = sma(candles, 20, i);
            if (i == 49) ema50 = sma(candles, 50, i);

            if (ema20 != null) {
                ema20 = EMACalculator.calculate(close, ema20, 20);
            }
            if (ema50 != null) {
                ema50 = EMACalculator.calculate(close, ema50, 50);
            }
        }

        if (ema20 != null && ema50 != null) {
            upsertLatestEma(scripCode, ema20, ema50, lastCandleTime);
        }
    }

    private BigDecimal sma(JsonNode candles, int period, int endIndex) {
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = endIndex - period + 1; i <= endIndex; i++) {
            sum = sum.add(candles.get(i).get(4).decimalValue());
        }
        return sum.divide(BigDecimal.valueOf(period), 4, RoundingMode.HALF_UP);
    }

    private void upsertLatestEma(
            int scripCode,
            BigDecimal ema20,
            BigDecimal ema50,
            LocalDateTime candleTime
    ) {

        Query query = new Query(
            Criteria.where("scripCode").is(scripCode)
                  
        );

        Update update = new Update()
                .set("ema20", ema20)
                .set("ema50", ema50)
                .set("lastCandleTime", candleTime)
                .set("updatedAt", Instant.now())
                .setOnInsert("scripCode", scripCode)
                .setOnInsert("interval", INTERVAL);

        mongoTemplate.upsert(query, update, COLLECTION);
    }
}
