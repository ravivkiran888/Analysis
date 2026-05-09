package com.analysis.scanner;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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

    // ===================== CONSTANTS =====================

    private static final int THREAD_POOL_SIZE = 8;

    private static final int SCAN_TIMEOUT_MINUTES = 4;

    private static final ZoneId IST_ZONE =
            ZoneId.of("Asia/Kolkata");

    // ===================== DEPENDENCIES =====================

    private final OptionsAPIClient optionsApiClient;

    private final MongoTemplate mongoTemplate;

    private final ObjectMapper objectMapper;

    private final OptionSymbolRepository optionSymbolRepository;

    private final ExecutorService executor;

    private final AtomicBoolean isScanning =
            new AtomicBoolean(false);

    @Value("${EXPIRY_DATE:}")
    private String expiryDate;

    // ===================== CONSTRUCTOR =====================

    public OptionsLevelScanner(
            MongoTemplate mongoTemplate,
            OptionsAPIClient optionsApiClient,
            OptionSymbolRepository optionSymbolRepository) {

        this.mongoTemplate = mongoTemplate;

        this.optionsApiClient = optionsApiClient;

        this.optionSymbolRepository =
                optionSymbolRepository;

        this.objectMapper = new ObjectMapper();

        /*
         * Bounded Thread Pool
         */
        this.executor = new ThreadPoolExecutor(
                THREAD_POOL_SIZE,
                THREAD_POOL_SIZE,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(100),
                new ThreadPoolExecutor.CallerRunsPolicy());

        log.info(
                "✅ OptionsLevelScanner initialized with thread pool size={}",
                THREAD_POOL_SIZE);
    }

    // ===================== MAIN SCAN =====================

    public void scan() {

        LocalTime now = LocalTime.now(IST_ZONE);

        /*
         * Prevent overlapping scans
         */
        if (!isScanning.compareAndSet(false, true)) {

            log.warn(
                    "⚠️ Previous scan still running. Skipping execution at {}",
                    now);

            return;
        }

        long startMillis = System.currentTimeMillis();

        try {

            executeScan(startMillis, now);

        } catch (Exception e) {

            log.error(
                    "❌ Fatal error during options scan",
                    e);

        } finally {

            isScanning.set(false);

            log.info(
                    "🔓 Scan lock released at {}",
                    LocalTime.now(IST_ZONE));
        }
    }

    // ===================== EXECUTE SCAN =====================

    private void executeScan(
            long startMillis,
            LocalTime startTime) {

        log.info(
                "🔍 Starting options scan at {} IST",
                startTime);

        List<OptionSymbol> allSymbols =
                optionSymbolRepository.findAll();

        List<String> symbols = allSymbols.stream()
                .map(OptionSymbol::getSymbol)
                .collect(Collectors.toList());

        if (symbols.isEmpty()) {

            log.warn("⚠️ No option symbols found");

            return;
        }

        CountDownLatch latch =
                new CountDownLatch(symbols.size());

        int[] success = {0};

        int[] failed = {0};

        for (String symbol : symbols) {

            try {

                executor.submit(() -> {

                    try {

                        Constants.RATE_LIMITER.acquire();

                        processSymbol(symbol);

                        synchronized (success) {
                            success[0]++;
                        }

                    } catch (Exception e) {

                        synchronized (failed) {
                            failed[0]++;
                        }

                        log.error(
                                "❌ Error processing symbol={}",
                                symbol,
                                e);

                    } finally {

                        latch.countDown();
                    }
                });

            } catch (RejectedExecutionException e) {

                failed[0]++;

                latch.countDown();

                log.error(
                        "❌ Task rejected for symbol={}",
                        symbol,
                        e);
            }
        }

        try {

            boolean completed = latch.await(
                    SCAN_TIMEOUT_MINUTES,
                    TimeUnit.MINUTES);

            long totalTime =
                    System.currentTimeMillis()
                            - startMillis;

            if (!completed) {

                log.warn(
                        "⚠️ Options scan timed out after {} minutes",
                        SCAN_TIMEOUT_MINUTES);

            } else {

                log.info(
                        "✅ Options scan completed in {} ms | Success={} Failed={}",
                        totalTime,
                        success[0],
                        failed[0]);
            }

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            log.error(
                    "❌ Options scan interrupted",
                    e);
        }
    }

    // ===================== PER SYMBOL PROCESSING =====================

    private void processSymbol(String symbol) {

        try {

            String response =
                    optionsApiClient.getOptionsChain(symbol);

            if (response == null
                    || response.isBlank()) {

                log.warn(
                        "⚠️ Empty API response for {}",
                        symbol);

                return;
            }

            JsonNode root =
                    objectMapper.readTree(response);

            if (!root.has("payload")) {

                throw new IllegalStateException(
                        "Missing payload");
            }

            JsonNode payload = root.get("payload");

            if (!payload.has("underlying_ltp")
                    || !payload.has("strikes")) {

                throw new IllegalStateException(
                        "Invalid payload structure");
            }

            double ltp =
                    payload.get("underlying_ltp")
                            .asDouble();

            JsonNode strikes =
                    payload.get("strikes");

            double atmStrike =
                    findATMStrike(strikes, ltp);

            JsonNode atmNode =
                    strikes.get(String.valueOf(atmStrike));

            if (atmNode == null) {

                atmNode =
                        strikes.get(
                                String.valueOf(
                                        (int) atmStrike));
            }

            if (atmNode == null) {

                throw new IllegalStateException(
                        "ATM strike not found");
            }

            JsonNode ce = atmNode.get("CE");

            JsonNode pe = atmNode.get("PE");

            if (ce == null || pe == null) {

                throw new IllegalStateException(
                        "CE/PE data missing");
            }

            // ===================== CURRENT VALUES =====================

            double ceOi =
                    ce.path("open_interest")
                            .asDouble(0);

            double peOi =
                    pe.path("open_interest")
                            .asDouble(0);

            double ceVol =
                    ce.path("volume")
                            .asDouble(0);

            double peVol =
                    pe.path("volume")
                            .asDouble(0);

            double ceIv =
                    ce.path("greeks")
                            .path("iv")
                            .asDouble(0);

            double peIv =
                    pe.path("greeks")
                            .path("iv")
                            .asDouble(0);

            // ===================== FETCH PREVIOUS =====================

            Query query = new Query(
                    Criteria.where("symbol")
                            .is(symbol)
                            .and("expiry")
                            .is(expiryDate));

            Document prev = mongoTemplate.findOne(
                    query,
                    Document.class,
                    "option_chain");

            double prevCeOi = 0;

            double prevPeOi = 0;

            double prevLtp = 0;

            double prevCeIv = 0;

            double prevPeIv = 0;

            if (prev != null) {

                prevLtp =
                        getDouble(prev, "ltp");

                Document ceDoc =
                        (Document) prev.get("ce");

                Document peDoc =
                        (Document) prev.get("pe");

                if (ceDoc != null) {

                    prevCeOi =
                            getDouble(ceDoc, "oi");

                    prevCeIv =
                            getDouble(ceDoc, "iv");
                }

                if (peDoc != null) {

                    prevPeOi =
                            getDouble(peDoc, "oi");

                    prevPeIv =
                            getDouble(peDoc, "iv");
                }
            }

            // ===================== DELTA =====================

            double ceOiChange =
                    ceOi - prevCeOi;

            double peOiChange =
                    peOi - prevPeOi;

            // ===================== OI BUILDUP =====================

            String oiBuildUp;

            if (peOiChange > 0 && ltp > prevLtp) {

                oiBuildUp = "PUT_WRITING";

            } else if (ceOiChange > 0
                    && ltp < prevLtp) {

                oiBuildUp = "CALL_WRITING";

            } else if (ceOiChange > 0
                    && ltp > prevLtp) {

                oiBuildUp = "LONG_BUILDUP";

            } else if (peOiChange > 0
                    && ltp < prevLtp) {

                oiBuildUp = "SHORT_BUILDUP";

            } else {

                oiBuildUp = "NEUTRAL";
            }

            // ===================== VOLUME SPIKE =====================

            String volumeSpike;

            if (peVol > ceVol * 1.5) {

                volumeSpike = "PE";

            } else if (ceVol > peVol * 1.5) {

                volumeSpike = "CE";

            } else {

                volumeSpike = "BALANCED";
            }

            // ===================== IV TREND =====================

            double avgIv =
                    (ceIv + peIv) / 2;

            double prevAvgIv =
                    (prevCeIv + prevPeIv) / 2;

            String ivTrend;

            if (avgIv > prevAvgIv + 2) {

                ivTrend = "RISING";

            } else if (avgIv < prevAvgIv - 2) {

                ivTrend = "FALLING";

            } else {

                ivTrend = "STABLE";
            }

            // ===================== PCR =====================

            double pcr =
                    ceOi == 0
                            ? (peOi > 0
                            ? Double.MAX_VALUE
                            : 0)
                            : peOi / ceOi;

            // ===================== SCORING =====================

            int score = 50;

            if (pcr > 1.2) {

                score += 15;

            } else if (pcr < 0.8) {

                score -= 15;
            }

            if ("PUT_WRITING".equals(oiBuildUp)) {

                score += 20;
            }

            if ("CALL_WRITING".equals(oiBuildUp)) {

                score -= 20;
            }

            if ("PE".equals(volumeSpike)) {

                score += 10;
            }

            if ("CE".equals(volumeSpike)) {

                score -= 10;
            }

            if ("RISING".equals(ivTrend)) {

                score += 5;
            }

            score = Math.max(
                    0,
                    Math.min(100, score));

            // ===================== ACTION =====================

            String action;

            String bias;

            if (score >= 70) {

                action = "BUY";

                bias = "BULLISH";

            } else if (score <= 30) {

                action = "SELL";

                bias = "BEARISH";

            } else if (score >= 45
                    && score <= 55) {

                action = "NO_TRADE";

                bias = "NEUTRAL";

            } else {

                action = "WAIT";

                bias = score > 50
                        ? "BULLISH"
                        : "BEARISH";
            }

            // ===================== UPDATE =====================

            Update update = new Update()

                    .set("timestamp", Instant.now())

                    .set("ltp", ltp)

                    .set("prev_ltp", prevLtp)

                    .set("atm_strike", atmStrike)

                    .set("pcr", pcr)

                    .set("confidence", score)

                    .set("bias", bias)

                    .set("action", action)

                    .set("ce.oi", ceOi)

                    .set("ce.prev_oi", prevCeOi)

                    .set("ce.volume", ceVol)

                    .set("ce.iv", ceIv)

                    .set("pe.oi", peOi)

                    .set("pe.prev_oi", prevPeOi)

                    .set("pe.volume", peVol)

                    .set("pe.iv", peIv)

                    .set("factors.oi_build_up",
                            oiBuildUp)

                    .set("factors.volume_spike",
                            volumeSpike)

                    .set("factors.iv_trend",
                            ivTrend)

                    .setOnInsert("symbol", symbol)

                    .setOnInsert("expiry",
                            expiryDate);

            mongoTemplate.upsert(
                    query,
                    update,
                    "option_chain");

        } catch (Exception e) {

            log.error(
                    "❌ Failed processing {}",
                    symbol,
                    e);
        }
    }

    // ===================== FIND ATM STRIKE =====================

    private double findATMStrike(
            JsonNode strikesNode,
            double ltp) {

        double closestStrike = 0;

        double minDiff = Double.MAX_VALUE;

        Iterator<Map.Entry<String, JsonNode>> fields =
                strikesNode.fields();

        while (fields.hasNext()) {

            Map.Entry<String, JsonNode> entry =
                    fields.next();

            double strike =
                    Double.parseDouble(
                            entry.getKey());

            double diff =
                    Math.abs(strike - ltp);

            if (diff < minDiff) {

                minDiff = diff;

                closestStrike = strike;
            }
        }

        return closestStrike;
    }

    // ===================== SAFE DOUBLE =====================

    private double getDouble(
            Document doc,
            String key) {

        Object val = doc.get(key);

        return val instanceof Number
                ? ((Number) val).doubleValue()
                : 0.0;
    }

    // ===================== CLEANUP =====================

    @PreDestroy
    public void cleanup() {

        log.info(
                "🛑 Shutting down OptionsLevelScanner executor");

        executor.shutdown();

        try {

            if (!executor.awaitTermination(
                    30,
                    TimeUnit.SECONDS)) {

                executor.shutdownNow();
            }

        } catch (InterruptedException e) {

            executor.shutdownNow();

            Thread.currentThread().interrupt();
        }

        log.info(
                "✅ Executor shutdown completed");
    }
}