package com.analysis.scanner;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

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
    private static final int EARLY_MIN_CANDLES = 3;                 // Start showing at 9:30 AM
    private static final int TRANSITION_CANDLES = 9;                // Until ~10:00 AM (9 candles)
    private static final int FULL_SCANNER_MIN_CANDLES = 10;         // Switch to full mode at ~10:05 AM
    private static final int THREAD_POOL_SIZE = 8;
    private static final int SCAN_TIMEOUT_MINUTES = 4;
    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");

    // Early scanner thresholds (relaxed)
    private static final BigDecimal EARLY_VOLUME_THRESHOLD = new BigDecimal("1.2");   // 1.2x surge
    private static final BigDecimal EARLY_PRICE_ABOVE_VWAP = BigDecimal.ZERO;         // > VWAP (any amount)

    // Full scanner thresholds (relaxed for more signals)
    private static final BigDecimal PRICE_ABOVE_VWAP_PERCENT = new BigDecimal("0.001");   // 0.1% (was 0.2%)
    private static final BigDecimal NORM_SLOPE_THRESHOLD = new BigDecimal("0.000001");    // (was 0.000005)
    private static final BigDecimal RSI_LOWER = new BigDecimal("58");
    private static final BigDecimal VOLUME_THRESHOLD = new BigDecimal("1.5");             // (was 1.7)

    // ATR-based thresholds
    private static final BigDecimal HARD_ATR_GUARD = new BigDecimal("2.5");       // force WAIT if extension > 2.5 * ATR
    private static final BigDecimal EXTENSION_ATR_PENALTY_1 = new BigDecimal("1.5"); // -1 if > 1.5 * ATR
    private static final BigDecimal EXTENSION_ATR_PENALTY_2 = new BigDecimal("2.2"); // -2 if > 2.2 * ATR
    private static final BigDecimal RSI_EXHAUSTION_ATR_LIMIT = new BigDecimal("2");   // for RSI > 80, require extension < 2 * ATR
    private static final BigDecimal SUPPORT_ATR_FACTOR = new BigDecimal("0.5");       // support proximity = 0.5 * ATR

    // Opening range: first 30 minutes = 6 candles (9:15 to 9:40)
    private static final int OPENING_RANGE_CANDLES = 6;

    // Signal states
    private static final String WATCH = "WATCH";
    private static final String EARLY_MOMENTUM = "EARLY_MOMENTUM";
    // Constants.ENTRY_READY and Constants.WAIT are assumed to exist

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
        
        // For testing, you can filter a specific symbol
        // allScripts = allScripts.stream().filter(e->e.getSymbol().equalsIgnoreCase("IDFCFIRSTB")).collect(Collectors.toList());
        
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
                        evaluateAndStoreEarly(scripCodeInt, script.getSymbol(),script.getSector(), candleCount);
                    } else {
                        evaluateAndStoreFull(scripCodeInt, script.getSymbol(), script.getSector() , candleCount);
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

    // -------------------- EARLY SCANNER (shows WATCH from 9:30 onward) --------------------
    private void evaluateAndStoreEarly(Integer scripCode, String symbol, String sector, int candleCount) throws Exception {
        String json = apiClient.getHistoricalData(scripCode, Constants.INTERVAL, 10);
        JsonNode candlesNode = objectMapper.readTree(json).path("data").path("candles");
        if (candlesNode.size() < candleCount) return;

        List<CandleData> allCandles = parseCandles(candlesNode);
        LocalDate today = LocalDate.now(IST_ZONE);
        List<CandleData> todaysCandles = allCandles.stream()
                .filter(c -> c.getTimestamp().toLocalDate().equals(today))
                .collect(Collectors.toList());

        if (todaysCandles.size() < candleCount) return;

        CandleData latest = todaysCandles.get(todaysCandles.size() - 1);
        Bhavcopy prevDay = bhavcopyService.getBhavcopyBySymbol(symbol);
        if (prevDay == null) return;

        // --- Volume baseline: weighted blend of previous day's avg and first 3 candles ---
        BigDecimal prevDayAvg = bhavcopyService.getAvgVolumePer5Min(prevDay);
        if (prevDayAvg == null || prevDayAvg.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Skipping {} due to invalid previous day volume", symbol);
            return;
        }

        BigDecimal baselineAvg;
        if (todaysCandles.size() >= 4) {
            List<CandleData> firstFew = todaysCandles.subList(0, 3);
            BigDecimal first3Avg = firstFew.stream()
                    .map(c -> BigDecimal.valueOf(c.getVolume()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP);
            // Weighted blend: 60% previous day, 40% first 3 candles
            baselineAvg = prevDayAvg.multiply(new BigDecimal("0.6"))
                            .add(first3Avg.multiply(new BigDecimal("0.4")))
                            .setScale(2, RoundingMode.HALF_UP);
        } else {
            baselineAvg = prevDayAvg;
        }

        BigDecimal volumeRatio = BigDecimal.valueOf(latest.getVolume())
                .divide(baselineAvg, 2, RoundingMode.HALF_UP);
        BigDecimal vwap = calculator.calculateVWAP(todaysCandles);

        // --- Guard against invalid VWAP ---
        if (vwap == null || vwap.compareTo(BigDecimal.ZERO) <= 0) {
            log.debug("Skipping {} due to invalid VWAP", symbol);
            return;
        }

        boolean priceAboveVWAP = latest.getClose().compareTo(vwap) > 0;
        boolean volumeSurge = volumeRatio.compareTo(EARLY_VOLUME_THRESHOLD) >= 0;

        // Determine signal: WATCH if both conditions true; ENTRY_READY if also above previous close/high
        boolean abovePrevClose = latest.getClose().compareTo(prevDay.getClsPric()) > 0;
        boolean abovePrevHigh = latest.getClose().compareTo(prevDay.getHghPric()) > 0;

        String signal;
        if (priceAboveVWAP && volumeSurge) {
            if (abovePrevClose || abovePrevHigh) {
                signal = Constants.ENTRY_READY;   // stronger signal
            } else {
                signal = WATCH;                    // early watch
            }
        } else {
            signal = Constants.WAIT;
        }

        // Early scanner does not compute support levels, so atStrongSupport = false
        storeIndicators(
                scripCode, symbol, todaysCandles.size(), Constants.EARLY,
                latest.getClose(), vwap, volumeRatio,
                null, null, null, null,
                null, null,
                signal, false,sector);
    }

    // -------------------- FULL SCANNER (with early momentum detection) --------------------
    private void evaluateAndStoreFull(Integer scripCode, String symbol, String sector , int candleCount) throws Exception {
        String json = apiClient.getHistoricalData(scripCode, Constants.INTERVAL, 60);
        JsonNode candlesNode = objectMapper.readTree(json).path("data").path("candles");
        if (candlesNode.size() < 50) return;

        List<CandleData> allCandles = parseCandles(candlesNode);
        LocalDate today = LocalDate.now(IST_ZONE);
        List<CandleData> todaysCandles = allCandles.stream()
                .filter(c -> c.getTimestamp().toLocalDate().equals(today))
                .collect(Collectors.toList());

        if (todaysCandles.isEmpty()) {
            log.debug("No intraday data for today for symbol {}", symbol);
            return;
        }

        CandleData latestToday = todaysCandles.get(todaysCandles.size() - 1);
        CandleData previousCandle = todaysCandles.size() > 1 ? todaysCandles.get(todaysCandles.size() - 2) : null;

        // --- Multi-day indicators ---
        BigDecimal ema20 = calculator.calculateEMA(allCandles, 20);
        List<CandleData> recentCandles = allCandles.size() > 42 ?
                allCandles.subList(allCandles.size() - 42, allCandles.size()) : allCandles;
        BigDecimal rsi = calculator.calculateRSI(recentCandles, 14);
        BigDecimal volumeExp = calculator.calculateVolumeExpansion(allCandles, 10);

        // --- Day-specific indicators ---
        BigDecimal vwap = calculator.calculateVWAP(todaysCandles);
        // --- Guard against invalid VWAP ---
        if (vwap == null || vwap.compareTo(BigDecimal.ZERO) <= 0) {
            log.debug("Skipping {} due to invalid VWAP", symbol);
            return;
        }

        BigDecimal vwapSlope = calculator.calculateVWAPSlope(todaysCandles);
        BigDecimal normalizedSlope = vwapSlope != null ?
                vwapSlope.divide(vwap, 10, RoundingMode.HALF_UP) : null;

        // --- ATR (14-period) with Wilder's smoothing ---
        BigDecimal atr14 = calculateWilderATR(allCandles, 14);
        if (atr14.compareTo(BigDecimal.ZERO) == 0) {
            log.debug("ATR zero for {}, cannot evaluate extension", symbol);
        }

        // --- Absolute extension and extension in ATR units ---
        BigDecimal absoluteExtension = latestToday.getClose().subtract(vwap).abs();
        BigDecimal extensionATR = atr14.compareTo(BigDecimal.ZERO) > 0 ?
                absoluteExtension.divide(atr14, 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        // --- HARD ATR GUARD: if extension > 2.5 * ATR, force WAIT immediately ---
        if (extensionATR.compareTo(HARD_ATR_GUARD) > 0) {
            log.debug("{} extension {} > {} * ATR, forcing WAIT", symbol, extensionATR, HARD_ATR_GUARD);
            storeIndicators(
                    scripCode, symbol, todaysCandles.size(), Constants.FULL,
                    latestToday.getClose(), vwap, volumeExp,
                    ema20, null, vwapSlope, rsi,
                    generateLevels(todaysCandles),
                    generateVolumeCommentary(todaysCandles),
                    Constants.WAIT, false, sector);
            return;
        }

        // --- EARLY MOMENTUM DETECTION (catch the very beginning of a move) ---
        boolean earlyMomentum = false;

        // 1. Price just above VWAP (up to 0.2% above)
        BigDecimal priceVsVWAP = latestToday.getClose().subtract(vwap)
                .divide(vwap, 6, RoundingMode.HALF_UP);
        boolean justAboveVWAP = priceVsVWAP.compareTo(BigDecimal.ZERO) > 0 &&
                                priceVsVWAP.compareTo(new BigDecimal("0.002")) <= 0; // up to 0.2%

        // 2. VWAP slope positive (any positive value)
        boolean slopePositive = normalizedSlope != null && normalizedSlope.compareTo(BigDecimal.ZERO) > 0;

        // 3. Volume expansion > 1.2x (lighter than the 1.5x used for ENTRY_READY)
        boolean volumeLightSurge = volumeExp != null && volumeExp.compareTo(new BigDecimal("1.2")) > 0;

        // 4. RSI not overbought (<65)
        boolean rsiNotOverbought = rsi == null || rsi.compareTo(new BigDecimal("65")) < 0;

        // 5. Green candle (close > previous close)
        boolean greenCandle = previousCandle != null &&
                              latestToday.getClose().compareTo(previousCandle.getClose()) > 0;

        // 6. Not extended (less than 1 ATR above VWAP)
        boolean notExtended = extensionATR.compareTo(BigDecimal.ONE) < 0;

        earlyMomentum = justAboveVWAP && slopePositive && volumeLightSurge && rsiNotOverbought && greenCandle && notExtended;

        // --- Opening Range Break ---
        boolean openingRangeBreak = checkOpeningRangeBreak(todaysCandles, previousCandle);

        // --- Last Hour Volume vs First Hour ---
        boolean lastHourVolumeStrong = checkLastHourVolume(todaysCandles);

        // --- 15-minute VWAP confirmation ---
        boolean above15MinVWAP = check15MinVWAP(todaysCandles, latestToday);

        // --- Core mandatory conditions (for condition counting) ---
        boolean priceAboveVWAP = priceVsVWAP.compareTo(PRICE_ABOVE_VWAP_PERCENT) >= 0; // original threshold 0.1%
        boolean slopePositiveStrict = normalizedSlope != null && normalizedSlope.compareTo(NORM_SLOPE_THRESHOLD) > 0;
        boolean volumeStrong = volumeExp != null && volumeExp.compareTo(VOLUME_THRESHOLD) > 0;

        // Log core values for debugging
        log.debug("{}: priceAboveVWAP={} (priceVsVWAP={}), slopePositiveStrict={} (normalizedSlope={}), volumeStrong={} (volumeExp={})",
                symbol, priceAboveVWAP, priceVsVWAP, slopePositiveStrict, normalizedSlope, volumeStrong, volumeExp);

        // --- Count conditions (max 8) ---
        int conditionsMet = 0;

        // 1. Price above VWAP (strict)
        if (priceAboveVWAP) conditionsMet++;

        // 2. Above EMA20
        if (ema20 != null && latestToday.getClose().compareTo(ema20) > 0) conditionsMet++;

        // 3. Normalized slope positive (strict)
        if (slopePositiveStrict) conditionsMet++;

        // 4. RSI > 60 and price rising, with exhaustion guard for RSI > 80
        boolean rsiCondition = false;
        if (rsi != null && previousCandle != null &&
            rsi.compareTo(new BigDecimal("60")) > 0 &&
            latestToday.getClose().compareTo(previousCandle.getClose()) > 0) {
            if (rsi.compareTo(new BigDecimal("80")) <= 0) {
                rsiCondition = true;
            } else {
                // RSI > 80: require extension < 2 * ATR
                if (extensionATR.compareTo(RSI_EXHAUSTION_ATR_LIMIT) < 0) {
                    rsiCondition = true;
                } else {
                    log.debug("RSI > 80 and extension >= {} * ATR, RSI condition false for {}", RSI_EXHAUSTION_ATR_LIMIT, symbol);
                }
            }
        }
        if (rsiCondition) conditionsMet++;

        // 5. Volume expansion strong
        if (volumeStrong) conditionsMet++;

        // 6. Opening range break
        if (openingRangeBreak) conditionsMet++;

        // 7. Last hour volume strong
        if (lastHourVolumeStrong) conditionsMet++;

        // 8. Above 15-min VWAP
        if (above15MinVWAP) conditionsMet++;

        // --- Apply ATR-based extension penalties ---
        if (extensionATR.compareTo(EXTENSION_ATR_PENALTY_1) > 0) {
            conditionsMet--; // -1 if > 1.5 * ATR
        }
        if (extensionATR.compareTo(EXTENSION_ATR_PENALTY_2) > 0) {
            conditionsMet--; // additional -1 if > 2.2 * ATR (total -2)
        }

        // --- Progressive required conditions based on time of day ---
        int requiredConditions;
        if (candleCount < TRANSITION_CANDLES) {
            // Before ~10:00 AM, require only 4 conditions (catch early movers)
            requiredConditions = 4;
        } else {
            // Later in the day, require full 5 conditions for confirmation
            requiredConditions = 5;
        }

        // --- Final Signal Determination (early momentum overrides) ---
        String signal;
        if (earlyMomentum) {
            signal = EARLY_MOMENTUM;
        } else if (conditionsMet >= requiredConditions) {
            signal = Constants.ENTRY_READY;
        } else if (conditionsMet >= 3) {
            signal = WATCH;
        } else {
            signal = Constants.WAIT;
        }

        log.debug("{} conditionsMet={} required={} signal={}", symbol, conditionsMet, requiredConditions, signal);

        // --- Generate support/resistance levels and check if at strong support (ATR-based) ---
        String levelInsights = generateLevels(todaysCandles);
        boolean atStrongSupport = isAtStrongSupport(todaysCandles, latestToday.getClose(), atr14);

        storeIndicators(
                scripCode, symbol, todaysCandles.size(), Constants.FULL,
                latestToday.getClose(), vwap, volumeExp,
                ema20, null, vwapSlope, rsi,
                levelInsights,
                generateVolumeCommentary(todaysCandles),
                signal, atStrongSupport, sector);
    }

    // -------------------- Helper methods for full scanner --------------------
    private boolean checkOpeningRangeBreak(List<CandleData> todaysCandles, CandleData previousCandle) {
        if (todaysCandles.size() < OPENING_RANGE_CANDLES + 1 || previousCandle == null) return false;
        BigDecimal openingRangeHigh = todaysCandles.subList(0, OPENING_RANGE_CANDLES).stream()
                .map(CandleData::getHigh)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        return previousCandle.getClose().compareTo(openingRangeHigh) > 0 &&
               todaysCandles.get(todaysCandles.size() - 1).getClose().compareTo(openingRangeHigh) > 0;
    }

    private boolean checkLastHourVolume(List<CandleData> todaysCandles) {
        if (todaysCandles.size() < 24) return false;
        List<CandleData> firstHourCandles = todaysCandles.subList(0, 12);
        List<CandleData> lastHourCandles = todaysCandles.subList(todaysCandles.size() - 12, todaysCandles.size());
        BigDecimal firstHourVol = firstHourCandles.stream()
                .map(c -> BigDecimal.valueOf(c.getVolume()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal lastHourVol = lastHourCandles.stream()
                .map(c -> BigDecimal.valueOf(c.getVolume()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return lastHourVol.compareTo(firstHourVol) > 0;
    }

    private boolean check15MinVWAP(List<CandleData> todaysCandles, CandleData latestToday) {
        if (todaysCandles.size() < 3) return false;
        List<CandleData> candles15 = aggregateTo15Min(todaysCandles);
        BigDecimal vwap15 = calculator.calculateVWAP(candles15);
        return vwap15 != null && latestToday.getClose().compareTo(vwap15) > 0;
    }

    // -------------------- WILDER'S ATR (14-period) --------------------
    private BigDecimal calculateWilderATR(List<CandleData> candles, int period) {
        if (candles.size() < period + 1) return BigDecimal.ZERO;

        // Compute true ranges
        List<BigDecimal> trueRanges = new ArrayList<>();
        for (int i = 1; i < candles.size(); i++) {
            CandleData curr = candles.get(i);
            CandleData prev = candles.get(i - 1);
            BigDecimal hl = curr.getHigh().subtract(curr.getLow()).abs();
            BigDecimal hc = curr.getHigh().subtract(prev.getClose()).abs();
            BigDecimal lc = curr.getLow().subtract(prev.getClose()).abs();
            BigDecimal tr = hl.max(hc).max(lc);
            trueRanges.add(tr);
        }

        // First ATR = SMA of first 'period' true ranges
        List<BigDecimal> firstTRs = trueRanges.subList(0, period);
        BigDecimal sum = firstTRs.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal atr = sum.divide(BigDecimal.valueOf(period), 4, RoundingMode.HALF_UP);

        // Wilder's smoothing for remaining values
        for (int i = period; i < trueRanges.size(); i++) {
            BigDecimal currentTR = trueRanges.get(i);
            atr = atr.multiply(BigDecimal.valueOf(period - 1))
                     .add(currentTR)
                     .divide(BigDecimal.valueOf(period), 4, RoundingMode.HALF_UP);
        }

        return atr.setScale(2, RoundingMode.HALF_UP);
    }

    // -------------------- CHECK IF PRICE IS AT STRONG SUPPORT (ATR-based) --------------------
    private boolean isAtStrongSupport(List<CandleData> candles, BigDecimal currentPrice, BigDecimal atr) {
        if (candles == null || candles.isEmpty() || atr == null || atr.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }

        BigDecimal dayHigh = candles.stream().map(CandleData::getHigh).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal dayLow = candles.stream().map(CandleData::getLow).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal close = candles.get(candles.size() - 1).getClose();

        BigDecimal pivot = dayHigh.add(dayLow).add(close)
                .divide(new BigDecimal("3"), 2, RoundingMode.HALF_UP);

        BigDecimal s1 = pivot.multiply(new BigDecimal("2"))
                .subtract(dayHigh).setScale(2, RoundingMode.HALF_UP);
        BigDecimal s2 = pivot.subtract(dayHigh.subtract(dayLow))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal roundSupport = roundToNearest(dayLow, 0.10); // same as in generateLevels

        BigDecimal threshold = atr.multiply(SUPPORT_ATR_FACTOR);

        // Check S1
        if (currentPrice.subtract(s1).abs().compareTo(threshold) <= 0) return true;
        // Check S2
        if (currentPrice.subtract(s2).abs().compareTo(threshold) <= 0) return true;
        // Check round support if it's between S1 and S2
        if (roundSupport.compareTo(s1) < 0 && roundSupport.compareTo(s2) > 0) {
            if (currentPrice.subtract(roundSupport).abs().compareTo(threshold) <= 0) return true;
        }
        return false;
    }

    // -------------------- HELPER: Aggregate to 15-minute candles --------------------
    private List<CandleData> aggregateTo15Min(List<CandleData> candles5min) {
        List<CandleData> result = new ArrayList<>();
        int i = 0;
        while (i + 2 < candles5min.size()) {
            List<CandleData> group = candles5min.subList(i, i + 3);
            LocalDateTime timestamp = group.get(2).getTimestamp();
            BigDecimal open = group.get(0).getOpen();
            BigDecimal high = group.stream().map(CandleData::getHigh).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
            BigDecimal low = group.stream().map(CandleData::getLow).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
            BigDecimal close = group.get(2).getClose();
            long volume = group.stream().mapToLong(CandleData::getVolume).sum();
            result.add(new CandleData(timestamp, open, high, low, close, volume));
            i += 3;
        }
        return result;
    }

    // -------------------- DYNAMIC VOLUME COMMENTARY --------------------
    private String generateVolumeCommentary(List<CandleData> candles) {
        if (candles == null || candles.isEmpty()) return "No volume data yet.";

        int totalCandles = candles.size();
        CandleData latest = candles.get(totalCandles - 1);
        String currentTime = latest.getTimestamp().format(DateTimeFormatter.ofPattern("HH:mm"));

        CandleData peakCandle = candles.stream()
                .max((a, b) -> Long.compare(a.getVolume(), b.getVolume()))
                .orElse(null);

        double avgVolume = candles.stream()
                .mapToLong(CandleData::getVolume)
                .average()
                .orElse(0);

        double currentRatio = latest.getVolume() / avgVolume;

        String currentDesc;
        if (currentRatio < 0.5) {
            currentDesc = "very light";
        } else if (currentRatio < 0.8) {
            currentDesc = "light";
        } else if (currentRatio < 1.2) {
            currentDesc = "normal";
        } else if (currentRatio < 1.5) {
            currentDesc = "strong";
        } else {
            currentDesc = "very strong";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(currentTime).append(": ");

        if (peakCandle != null) {
            String peakTime = peakCandle.getTimestamp().format(DateTimeFormatter.ofPattern("HH:mm"));
            long peakVol = peakCandle.getVolume();

            if (peakCandle.equals(latest)) {
                sb.append(String.format("Current volume is a new high spike! (%s). ",
                        formatVolume(peakVol)));
            } else {
                sb.append(String.format("Strong spike at %s (%s) now faded. ",
                        peakTime, formatVolume(peakVol)));
            }
        }

        sb.append(String.format("Current volume %s (%.2fx avg).",
                currentDesc, currentRatio));

        return sb.toString();
    }

    private String formatVolume(long vol) {
        if (vol >= 1_000_000) return String.format("%.1fM", vol / 1_000_000.0);
        if (vol >= 1_000) return String.format("%.1fK", vol / 1_000.0);
        return Long.toString(vol);
    }

    private BigDecimal roundToNearest(BigDecimal value, double step) {
        BigDecimal stepBD = BigDecimal.valueOf(step);
        BigDecimal divided = value.divide(stepBD, 0, RoundingMode.HALF_UP);
        return divided.multiply(stepBD);
    }

    // -------------------- LEVEL GENERATION (Markdown table) --------------------
    private String generateLevels(List<CandleData> candles) {
        if (candles == null || candles.isEmpty()) {
            return "No intraday data available.";
        }

        BigDecimal dayHigh = candles.stream().map(CandleData::getHigh).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal dayLow = candles.stream().map(CandleData::getLow).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal close = candles.get(candles.size() - 1).getClose();

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

        BigDecimal roundResistance = roundToNearest(close, 0.50);
        BigDecimal roundSupport = roundToNearest(dayLow, 0.10);

        StringBuilder sb = new StringBuilder();
        sb.append("📊 **Key Levels for Next Session**\n\n");
        sb.append("| Level | Price | Action |\n");
        sb.append("|-------|-------|--------|\n");
        sb.append(String.format("| R2    | %s | Day High |\n", dayHigh));
        sb.append(String.format("| R1    | %s | Primary Target |\n", r1));
        if (roundResistance.compareTo(r1) > 0 && roundResistance.compareTo(dayHigh) < 0) {
            sb.append(String.format("| R (round) | %s | Psychological resistance |\n", roundResistance));
        }
        sb.append(String.format("| S1    | %s | Key Support |\n", s1));
        sb.append(String.format("| S2    | %s | Strong Support |\n", s2));
        if (roundSupport.compareTo(s1) < 0 && roundSupport.compareTo(s2) > 0) {
            sb.append(String.format("| S (round) | %s | Recent low support |\n", roundSupport));
        }

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

    // -------------------- STORAGE (with atStrongSupport) --------------------
    private void storeIndicators(
            Integer scripCode, String symbol, int candleCount, String mode,
            BigDecimal price, BigDecimal vwap, BigDecimal volumeExpansion,
            BigDecimal ema20, BigDecimal ema50, BigDecimal vwapSlope, BigDecimal rsi,
            String levelInsights, String volumeCommentary, String signal,
            boolean atStrongSupport , String sector) {

        Query query = new Query(Criteria.where(Constants.SCRIPT_CODE).is(String.valueOf(scripCode)));
        Update update = new Update()
                .set("symbol", symbol)
                .set("timestamp", Instant.now())
                .set("candleCount", candleCount)
                .set("mode", mode)
                .set("price", price)
                .set("vwap", vwap)
                .set("volumeExpansion", volumeExpansion)
                .set("levelInsights", levelInsights)
                .set("volumeCommentary", volumeCommentary)
                .set("signal", signal)
                .set("sector", sector)
                .set("atStrongSupport", atStrongSupport);

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

    /**
     * Parses candle data from JSON and converts timestamps from UTC to IST.
     */
    private List<CandleData> parseCandles(JsonNode candlesNode) {
        List<CandleData> list = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        
        for (JsonNode c : candlesNode) {
            // Parse the timestamp as UTC (API returns UTC)
            LocalDateTime utcTime = LocalDateTime.parse(c.get(0).asText(), fmt);
            
            // Convert UTC to IST (UTC+5:30)
            ZonedDateTime utcZoned = utcTime.atZone(ZoneId.of("UTC"));
            ZonedDateTime istZoned = utcZoned.withZoneSameInstant(IST_ZONE);
            LocalDateTime istTime = istZoned.toLocalDateTime();
            
            list.add(new CandleData(
                    istTime,                     // Now in IST
                    c.get(1).decimalValue(),     // open
                    c.get(2).decimalValue(),     // high
                    c.get(3).decimalValue(),     // low
                    c.get(4).decimalValue(),     // close
                    c.get(5).longValue()         // volume
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