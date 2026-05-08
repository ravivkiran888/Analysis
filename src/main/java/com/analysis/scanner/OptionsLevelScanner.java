package com.analysis.scanner;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import com.analysis.apicalls.OptionsAPIClient;
import com.analysis.constants.Constants;
import com.analysis.documents.OptionSymbol;
import com.analysis.repository.OptionSymbolRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class OptionsLevelScanner {

    private static final int THREAD_POOL_SIZE = 8;

    private static final int SCAN_TIMEOUT_MINUTES = 4;

    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");

    private final OptionsAPIClient optionsApiClient;
    private final MongoTemplate mongoTemplate;
    private final ObjectMapper objectMapper;
    private final OptionSymbolRepository optionSymbolRepository;

    private final ExecutorService executor;

    private final AtomicBoolean isScanning = new AtomicBoolean(false);

    @Value("${EXPIRY_DATE:}")
    private String expiryDate;

    public OptionsLevelScanner(
            MongoTemplate mongoTemplate,
            OptionsAPIClient optionsApiClient,
            OptionSymbolRepository optionSymbolRepository) {

        this.mongoTemplate = mongoTemplate;
        this.optionsApiClient = optionsApiClient;
        this.optionSymbolRepository = optionSymbolRepository;

        this.objectMapper = new ObjectMapper();

        this.executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        log.info("✅ OptionsLevelScanner initialized");
    }

    public void scan() {

        LocalTime now = LocalTime.now(IST_ZONE);

        // Prevent overlapping execution
        if (!isScanning.compareAndSet(false, true)) {
            log.warn("⚠️ Previous scan still running. Skipping current execution.");
            return;
        }

        long startMillis = System.currentTimeMillis();

        try {

            log.info("🔍 Scan started at {}", now);

            List<OptionSymbol> symbolsList = optionSymbolRepository.findAll();

            List<String> symbols = symbolsList.stream()
                    .map(OptionSymbol::getSymbol)
                    .collect(Collectors.toList());

            if (symbols.isEmpty()) {
                log.warn("⚠️ No symbols found");
                return;
            }

            CountDownLatch latch = new CountDownLatch(symbols.size());

            AtomicInteger success = new AtomicInteger();
            AtomicInteger failed = new AtomicInteger();

            for (String symbol : symbols) {

                executor.submit(() -> {

                    try {

                        // Rate limiting
                        Constants.RATE_LIMITER.acquire();

                        processSymbol(symbol);

                        success.incrementAndGet();

                    } catch (Exception e) {

                        failed.incrementAndGet();

                        log.error("❌ Error processing {} : {}", symbol, e.getMessage(), e);

                    } finally {

                        latch.countDown();
                    }
                });
            }

            boolean completed = latch.await(
                    SCAN_TIMEOUT_MINUTES,
                    TimeUnit.MINUTES);

            long totalTime = System.currentTimeMillis() - startMillis;

            if (!completed) {

                log.warn(
                        "⚠️ Scan timed out after {} minutes",
                        SCAN_TIMEOUT_MINUTES);

            } else {

                log.info(
                        "✅ Scan completed in {} ms | Success={} Failed={}",
                        totalTime,
                        success.get(),
                        failed.get());
            }

        } catch (Exception e) {

            log.error("❌ Fatal scan error", e);

        } finally {

            isScanning.set(false);

            log.info("🔓 Scan lock released");
        }
    }

    private void processSymbol(String symbol) {

        try {

            String response = optionsApiClient.getOptionsChain(symbol);

            if (response == null || response.isBlank()) {
                log.warn("⚠️ Empty response for {}", symbol);
                return;
            }

            JsonNode root = objectMapper.readTree(response);

            JsonNode payload = root.path("payload");

            if (payload.isMissingNode()) {
                log.warn("⚠️ Missing payload for {}", symbol);
                return;
            }

            double ltp = payload.path("underlying_ltp").asDouble();

            JsonNode strikes = payload.path("strikes");

            if (strikes.isMissingNode()) {
                log.warn("⚠️ Missing strikes for {}", symbol);
                return;
            }

            double atmStrike = findATMStrike(strikes, ltp);

            JsonNode atmNode = strikes.get(String.valueOf((int) atmStrike));

            if (atmNode == null) {
                atmNode = strikes.get(String.valueOf(atmStrike));
            }

            if (atmNode == null) {
                log.warn("⚠️ ATM strike missing for {}", symbol);
                return;
            }

            JsonNode ce = atmNode.path("CE");
            JsonNode pe = atmNode.path("PE");

            double ceOi = ce.path("open_interest").asDouble(0);
            double peOi = pe.path("open_interest").asDouble(0);

            double ceVol = ce.path("volume").asDouble(0);
            double peVol = pe.path("volume").asDouble(0);

            double ceIv = ce.path("greeks").path("iv").asDouble(0);
            double peIv = pe.path("greeks").path("iv").asDouble(0);

            Query query = new Query(
                    Criteria.where("symbol").is(symbol)
                            .and("expiry").is(expiryDate));

            Document prev = mongoTemplate.findOne(
                    query,
                    Document.class,
                    "option_chain");

            double prevCeOi = 0;
            double prevPeOi = 0;
            double prevLtp = 0;
            double prevCeIv = 0;

            if (prev != null) {

                prevLtp = getDouble(prev, "ltp");

                Document ceDoc = (Document) prev.get("ce");
                Document peDoc = (Document) prev.get("pe");

                if (ceDoc != null) {
                    prevCeOi = getDouble(ceDoc, "oi");
                    prevCeIv = getDouble(ceDoc, "iv");
                }

                if (peDoc != null) {
                    prevPeOi = getDouble(peDoc, "oi");
                }
            }

            double pcr = ceOi == 0 ? 0 : peOi / ceOi;

            Update update = new Update()

                    .set("timestamp", Instant.now())

                    .set("symbol", symbol)

                    .set("expiry", expiryDate)

                    .set("ltp", ltp)

                    .set("prev_ltp", prevLtp)

                    .set("atm_strike", atmStrike)

                    .set("pcr", pcr)

                    .set("ce.oi", ceOi)

                    .set("ce.prev_oi", prevCeOi)

                    .set("ce.volume", ceVol)

                    .set("ce.iv", ceIv)

                    .set("pe.oi", peOi)

                    .set("pe.prev_oi", prevPeOi)

                    .set("pe.volume", peVol)

                    .set("pe.iv", peIv);

            mongoTemplate.upsert(
                    query,
                    update,
                    "option_chain");

            log.info("✅ Processed {}", symbol);

        } catch (Exception e) {

            log.error("❌ Failed processing {}", symbol, e);
        }
    }

    private double findATMStrike(JsonNode strikesNode, double ltp) {

        double closestStrike = 0;

        double minDiff = Double.MAX_VALUE;

        Iterator<Map.Entry<String, JsonNode>> fields = strikesNode.fields();

        while (fields.hasNext()) {

            Map.Entry<String, JsonNode> entry = fields.next();

            double strike = Double.parseDouble(entry.getKey());

            double diff = Math.abs(strike - ltp);

            if (diff < minDiff) {

                minDiff = diff;

                closestStrike = strike;
            }
        }

        return closestStrike;
    }

    private double getDouble(Document doc, String key) {

        Object value = doc.get(key);

        return value instanceof Number
                ? ((Number) value).doubleValue()
                : 0.0;
    }

    @PreDestroy
    public void cleanup() {

        log.info("🛑 Shutting down executor");

        executor.shutdown();

        try {

            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {

                executor.shutdownNow();
            }

        } catch (InterruptedException e) {

            executor.shutdownNow();

            Thread.currentThread().interrupt();
        }

        log.info("✅ Executor shutdown completed");
    }
}