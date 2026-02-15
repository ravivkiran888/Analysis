package com.analysis.scanner;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import com.analysis.apicalls.APIClient;
import com.analysis.constants.Constants;
import com.analysis.documents.Bhavcopy;
import com.analysis.documents.ScripMaster;
import com.analysis.documents.SymbolIndicators;
import com.analysis.model.CandleData;
import com.analysis.repository.ScripMasterRepository;
import com.analysis.scanner.util.TradingCalculator;
import com.analysis.service.BhavcopyService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class SymbolScanner {

    // ===================== CONSTANTS =====================
    private static final int EARLY_MIN_CANDLES = 3;
    private static final int FULL_SCANNER_MIN_CANDLES = 15;
    private static final int THREAD_POOL_SIZE = 8;
    private static final int SCAN_TIMEOUT_MINUTES = 4;
    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");

    // Early scanner thresholds
    private static final BigDecimal EARLY_VOLUME_THRESHOLD = new BigDecimal("1.5");

    // Full scanner thresholds
    private static final BigDecimal PRICE_ABOVE_VWAP_PERCENT = new BigDecimal("0.002");
    private static final BigDecimal EMA_ALIGNMENT_PERCENT = new BigDecimal("0.001");
    private static final BigDecimal VWAP_SLOPE_THRESHOLD = new BigDecimal("0.0005");
    private static final BigDecimal RSI_LOWER = new BigDecimal("58");
    private static final BigDecimal RSI_UPPER = new BigDecimal("75");
    private static final BigDecimal VOLUME_THRESHOLD = new BigDecimal("1.7");

    private final APIClient apiClient;
    private final TradingCalculator calculator;
    private final MongoTemplate mongoTemplate;
    private final BhavcopyService bhavcopyService;
    private final ObjectMapper objectMapper;
    private final ScripMasterRepository scripMasterRepository;
    private final ExecutorService executor;
    private final AtomicBoolean isScanning = new AtomicBoolean(false);
    private volatile LocalTime lastScanStartTime = null;

    public SymbolScanner(
            MongoTemplate mongoTemplate,
            APIClient apiClient,
            TradingCalculator calculator,
            BhavcopyService bhavcopyService,
            ScripMasterRepository scripMasterRepository) {
        this.mongoTemplate = mongoTemplate;
        this.bhavcopyService = bhavcopyService;
        this.objectMapper = new ObjectMapper();
        this.apiClient = apiClient;
        this.calculator = calculator;
        this.scripMasterRepository = scripMasterRepository;
        this.executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        log.info("Initialized thread pool with {} threads", THREAD_POOL_SIZE);
    }

    // -------------------- PUBLIC SCAN METHOD --------------------
    public void scan() {
        LocalTime now = LocalTime.now(IST_ZONE);
        if (!isScanning.compareAndSet(false, true)) {
            log.warn("⚠️ Previous scan still running at {}, skipping this execution", now);
            return;
        }
        lastScanStartTime = now;
        try {
            executeScan(now);
        } catch (Exception e) {
            log.error("Fatal error during scan", e);
        } finally {
            isScanning.set(false);
            log.info("Scan lock released at {}", LocalTime.now(IST_ZONE));
        }
    }

    private void executeScan(LocalTime startTime) {
        int candleCount = getCandlesSoFar(startTime);
        if (candleCount < EARLY_MIN_CANDLES) {
            log.debug("Too early, only {} candles.", candleCount);
            return;
        }

        log.info("🔍 Starting scan at {} IST ({} candles so far)", startTime, candleCount);
        List<ScripMaster> allScripts = scripMasterRepository.findAll();
        CountDownLatch latch = new CountDownLatch(allScripts.size());
        AtomicInteger processed = new AtomicInteger(0);
        AtomicInteger successful = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);

        for (ScripMaster script : allScripts) {
            if (shouldAbortScan(startTime)) {
                log.warn("⚠️ Aborting scan at {} IST to prevent overlap", LocalTime.now(IST_ZONE));
                break;
            }
            executor.submit(() -> {
                try {
                    Constants.RATE_LIMITER.acquire();
                    Integer scripCodeInt = Integer.parseInt(script.getScripCode());
                    if (candleCount < FULL_SCANNER_MIN_CANDLES) {
                        evaluateAndStoreEarly(scripCodeInt, script.getSymbol(), candleCount);
                    } else {
                        evaluateAndStoreFull(scripCodeInt, script.getSymbol(), candleCount);
                    }
                    successful.incrementAndGet();
                } catch (Exception e) {
                    failed.incrementAndGet();
                    log.error("Error processing {}: {}", script.getSymbol(), e.getMessage());
                } finally {
                    processed.incrementAndGet();
                    latch.countDown();
                }
            });
        }

        try {
            boolean completed = latch.await(SCAN_TIMEOUT_MINUTES, TimeUnit.MINUTES);
            if (!completed) {
                log.warn("⚠️ Scan timed out after {} minutes. Completed: {}/{}",
                        SCAN_TIMEOUT_MINUTES, processed.get(), allScripts.size());
            } else {
                log.info("✅ Scan completed in {} ms at {} IST (Success: {}, Failed: {})",
                        System.currentTimeMillis() - startTime.toNanoOfDay() / 1000000,
                        LocalTime.now(IST_ZONE), successful.get(), failed.get());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Scan was interrupted", e);
        }
    }

    private boolean shouldAbortScan(LocalTime startTime) {
        long elapsedSeconds = java.time.Duration.between(startTime, LocalTime.now(IST_ZONE)).getSeconds();
        return elapsedSeconds > 240; // 4 minutes
    }

    // -------------------- EARLY SCANNER --------------------
    private void evaluateAndStoreEarly(Integer scripCode, String symbol, int candleCount) throws Exception {
        String json = apiClient.getHistoricalData(scripCode, Constants.INTERVAL, 10);
        JsonNode candlesNode = objectMapper.readTree(json).path("data").path("candles");
        if (candlesNode.size() < candleCount) return;

        List<CandleData> candles = parseCandles(candlesNode);
        CandleData latest = candles.get(candles.size() - 1);
        Bhavcopy prevDay = bhavcopyService.getBhavcopyBySymbol(symbol);
        if (prevDay == null) return;

        BigDecimal vwap = calculator.calculateVWAP(candles);
        BigDecimal avgVol5min = bhavcopyService.getAvgVolumePer5Min(prevDay);
        BigDecimal volumeRatio = BigDecimal.valueOf(latest.getVolume())
                .divide(avgVol5min, 2, RoundingMode.HALF_UP);

        boolean priceAboveVWAP = latest.getClose().compareTo(vwap) > 0;
        boolean volumeSurge = volumeRatio.compareTo(EARLY_VOLUME_THRESHOLD) >= 0;
        boolean abovePrevHigh = latest.getClose().compareTo(prevDay.getHghPric()) > 0;
        boolean abovePrevClose = latest.getClose().compareTo(prevDay.getClsPric()) > 0;

        String signal = (priceAboveVWAP && volumeSurge && (abovePrevHigh || abovePrevClose))
                ? Constants.ENTRY_READY : Constants.WAIT;

        storeIndicators(
                scripCode, symbol, candleCount, Constants.EARLY,
                latest.getClose(), vwap, volumeRatio,
                null, null, null, null, null, // no level insights for early scans (can be added later)
                signal);
    }

    // -------------------- FULL SCANNER --------------------
    private void evaluateAndStoreFull(Integer scripCode, String symbol, int candleCount) throws Exception {
        String json = apiClient.getHistoricalData(scripCode, Constants.INTERVAL, 60);
        JsonNode candlesNode = objectMapper.readTree(json).path("data").path("candles");
        if (candlesNode.size() < 50) return;

        List<CandleData> candles = parseCandles(candlesNode);
        CandleData latest = candles.get(candles.size() - 1);

        BigDecimal vwap = calculator.calculateVWAP(candles);
        BigDecimal vwapSlope = calculator.calculateVWAPSlope(candles);
        BigDecimal ema20 = calculator.calculateEMA(candles, 20);
        BigDecimal ema50 = calculator.calculateEMA(candles, 50);
        BigDecimal rsi = calculator.calculateRSI(candles, 14);
        BigDecimal volumeExp = calculator.calculateVolumeExpansion(candles, 10);

        int conditionsMet = 0;
        BigDecimal priceVsVWAP = latest.getClose().subtract(vwap)
                .divide(vwap, 6, RoundingMode.HALF_UP);
        if (priceVsVWAP.compareTo(PRICE_ABOVE_VWAP_PERCENT) >= 0) conditionsMet++;

        if (ema20 != null && ema50 != null) {
            BigDecimal emaDiff = ema20.subtract(ema50).divide(ema50, 6, RoundingMode.HALF_UP);
            if (emaDiff.compareTo(EMA_ALIGNMENT_PERCENT) >= 0) conditionsMet++;
        }

        if (vwapSlope != null && vwapSlope.compareTo(VWAP_SLOPE_THRESHOLD) > 0) conditionsMet++;
        if (rsi != null && rsi.compareTo(RSI_LOWER) >= 0 && rsi.compareTo(RSI_UPPER) <= 0) conditionsMet++;
        if (volumeExp != null && volumeExp.compareTo(VOLUME_THRESHOLD) > 0) conditionsMet++;

        String signal = (conditionsMet >= 4) ? Constants.ENTRY_READY : Constants.WAIT;

        // --- Generate support/resistance levels from the day's candles ---
        String levelInsights = generateLevels(candles);

        storeIndicators(
                scripCode, symbol, candleCount, Constants.FULL,
                latest.getClose(), vwap, volumeExp,
                ema20, ema50, vwapSlope, rsi,
                levelInsights,
                signal);
    }

    // -------------------- LEVEL GENERATION --------------------
    /**
     * Generates support/resistance levels with actionable commentary.
     */
    private String generateLevels(List<CandleData> candles) {
        if (candles == null || candles.isEmpty()) {
            return "No intraday data available.";
        }

        // Day high, low, and closing price
        BigDecimal dayHigh = candles.stream().map(CandleData::getHigh).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal dayLow = candles.stream().map(CandleData::getLow).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal close = candles.get(candles.size() - 1).getClose();

        // Pivot points
        BigDecimal pivot = dayHigh.add(dayLow).add(close)
                .divide(new BigDecimal("3"), 2, RoundingMode.HALF_UP);

        BigDecimal r1 = pivot.multiply(new BigDecimal("2"))
                .subtract(dayLow).setScale(2, RoundingMode.HALF_UP);
        BigDecimal r2 = pivot.add(dayHigh.subtract(dayLow))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal s1 = pivot.multiply(new BigDecimal("2"))
                .subtract(dayHigh).setScale(2, RoundingMode.HALF_UP);
        BigDecimal s2 = pivot.subtract(dayHigh.subtract(dayLow))
                .setScale(2, RoundingMode.HALF_UP);

        // Identify round numbers (e.g., 82.00, 81.00) that may act as psychological levels
        BigDecimal roundResistance = new BigDecimal("82.00"); // Could be made dynamic based on price range
        BigDecimal roundSupport = new BigDecimal("81.40");   // Example – adjust per stock

        StringBuilder sb = new StringBuilder();
        sb.append("📊 **Key Levels for Next Session**\n\n");

        sb.append("**Resistance 1:** ").append(r1)
          .append(" – Break with volume → buy\n");
        sb.append("**Resistance 2:** ").append(dayHigh)
          .append(" – Day high – profit booking zone\n");
        if (roundResistance.compareTo(r1) > 0 && roundResistance.compareTo(dayHigh) < 0) {
            sb.append("**Resistance (round):** ").append(roundResistance)
              .append(" – Psychological level; watch for reaction\n");
        }

        sb.append("**Support 1:** ").append(s1)
          .append(" – Below this → further downside\n");
        sb.append("**Support 2:** ").append(s2)
          .append(" – Previous consolidation – strong support\n");
        if (roundSupport.compareTo(s1) < 0 && roundSupport.compareTo(s2) > 0) {
            sb.append("**Support (recent low):** ").append(roundSupport)
              .append(" – Late sell-off low; breakdown would weaken structure\n");
        }

        // Volume insight (last hour)
        long lastHourVolume = candles.stream()
                .filter(c -> c.getTimestamp().getHour() >= 14)
                .mapToLong(CandleData::getVolume).sum();
        sb.append("\n📈 **Volume Insight:**\n");
        if (lastHourVolume > 1_000_000) {
            sb.append("– Heavy volume in last hour (selling pressure). Cautious on longs near close.\n");
        } else {
            sb.append("– Volume tapered in last hour; typical end-of-day activity.\n");
        }

        return sb.toString();
    }

    // -------------------- STORAGE --------------------
    private void storeIndicators(
            Integer scripCode, String symbol, int candleCount, String mode,
            BigDecimal price, BigDecimal vwap, BigDecimal volumeExpansion,
            BigDecimal ema20, BigDecimal ema50, BigDecimal vwapSlope, BigDecimal rsi,
            String levelInsights, String signal) {

        Query query = new Query(Criteria.where(Constants.SCRIPT_CODE).is(String.valueOf(scripCode)));
        Update update = new Update()
                .set("symbol", symbol)
                .set("timestamp", Instant.now())
                .set("candleCount", candleCount)
                .set("mode", mode)
                .set("price", price)
                .set("vwap", vwap)
                .set("volumeExpansion", volumeExpansion)
                .set("levelInsights", levelInsights)    // new field
                .set("signal", signal);

        if (Constants.FULL.equals(mode)) {
            if (ema20 != null) update.set("ema20", ema20);
            if (ema50 != null) update.set("ema50", ema50);
            if (vwapSlope != null) update.set("vwapSlope", vwapSlope);
            if (rsi != null) update.set("rsi", rsi);
        } else {
            update.set("ema20", null);
            update.set("ema50", null);
            update.set("vwapSlope", null);
            update.set("rsi", null);
        }

        mongoTemplate.upsert(query, update, SymbolIndicators.class);
    }

    // -------------------- UTILITIES --------------------
    private int getCandlesSoFar(LocalTime now) {
        int openMinute = 9 * 60 + 15; // 9:15
        int currentMinute = now.getHour() * 60 + now.getMinute();
        if (currentMinute < openMinute) return 0;
        return ((currentMinute - openMinute) / 5) + 1;
    }

    private List<CandleData> parseCandles(JsonNode candlesNode) {
        List<CandleData> list = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        for (JsonNode c : candlesNode) {
            list.add(new CandleData(
                    LocalDateTime.parse(c.get(0).asText(), fmt),
                    c.get(1).decimalValue(),
                    c.get(2).decimalValue(),
                    c.get(3).decimalValue(),
                    c.get(4).decimalValue(),
                    c.get(5).longValue()
            ));
        }
        return list;
    }

    public boolean isScanning() { return isScanning.get(); }
    public LocalTime getLastScanStartTime() { return lastScanStartTime; }

    @PreDestroy
    public void cleanup() {
        log.info("Shutting down thread pool...");
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                    log.error("Thread pool did not terminate");
                }
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("Thread pool shut down successfully");
    }
}