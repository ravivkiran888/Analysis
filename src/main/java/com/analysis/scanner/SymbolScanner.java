package com.analysis.scanner;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
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

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class SymbolScanner {

    // ===================== CONSTANTS =====================
    private static final int EARLY_MIN_CANDLES = 3;          // start early scanner at 9:30
    private static final int FULL_SCANNER_MIN_CANDLES = 15;  // switch at 10:30

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
    private final ExecutorService executor;
    private final ScripMasterRepository scripMasterRepository;

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
        this.executor = Executors.newFixedThreadPool(8);
        this.calculator = calculator;
        this.scripMasterRepository = scripMasterRepository;
    }

    @Scheduled(cron = "0 */5 9-15 * * MON-FRI")
    public void scan() {
        LocalTime now = LocalTime.now();
        int candleCount = getCandlesSoFar(now);

        if (candleCount < EARLY_MIN_CANDLES) {
            log.debug("Too early, only {} candles.", candleCount);
            return;
        }

        log.info("🔍 Starting scan at {} ({} candles so far)", now, candleCount);

        List<ScripMaster> allScripts = scripMasterRepository.findAll();
        
        
        AtomicInteger processed = new AtomicInteger(0);

        for (ScripMaster script : allScripts) {
            executor.submit(() -> {
                try {
                    Constants.RATE_LIMITER.acquire();
                    
                    // Convert String to Integer once
                    Integer scripCodeInt = Integer.parseInt(script.getScripCode());
                    
                    if (candleCount < FULL_SCANNER_MIN_CANDLES) {
                        evaluateAndStoreEarly(scripCodeInt, script.getSymbol(), candleCount);
                    } else {
                        evaluateAndStoreFull(scripCodeInt, script.getSymbol(), candleCount);
                    }
                } catch (Exception e) {
                    log.error("Error processing {}: {}", script.getSymbol(), e.getMessage());
                } finally {
                    int count = processed.incrementAndGet();
                    if (count % 25 == 0) {
                        
                    }
                }
            });
        }

        while (processed.get() < allScripts.size()) {
            try { Thread.sleep(100); } catch (InterruptedException e) { 
                Thread.currentThread().interrupt(); 
            }
        }

        log.info("✅ Scan completed at {}", now);
    }

    // ===================== EARLY SCANNER =====================
    
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

        // Mandatory conditions
        boolean priceAboveVWAP = latest.getClose().compareTo(vwap) > 0;
        boolean volumeSurge = volumeRatio.compareTo(EARLY_VOLUME_THRESHOLD) >= 0;

        // Optional conditions
        boolean abovePrevHigh = latest.getClose().compareTo(prevDay.getHghPric()) > 0;
        boolean abovePrevClose = latest.getClose().compareTo(prevDay.getClsPric()) > 0;

        // Determine signal
        String signal;
        if (priceAboveVWAP && volumeSurge && (abovePrevHigh || abovePrevClose)) {
            signal = Constants.ENTRY_READY;
        } else {
            signal = Constants.WAIT;
        }

 
        // Store in MongoDB
        storeIndicators(
                scripCode, symbol, candleCount, Constants.EARLY,
                latest.getClose(), vwap, volumeRatio,
                null, null, null, null, signal
        );
    }
    
    // ===================== FULL SCANNER =====================
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
            BigDecimal emaDiff = ema20.subtract(ema50)
                    .divide(ema50, 6, RoundingMode.HALF_UP);
            if (emaDiff.compareTo(EMA_ALIGNMENT_PERCENT) >= 0) conditionsMet++;
        }

        if (vwapSlope.compareTo(VWAP_SLOPE_THRESHOLD) > 0) conditionsMet++;

        if (rsi != null && rsi.compareTo(RSI_LOWER) >= 0 && rsi.compareTo(RSI_UPPER) <= 0) conditionsMet++;

        if (volumeExp.compareTo(VOLUME_THRESHOLD) > 0) conditionsMet++;

        String signal = (conditionsMet >= 4) ? Constants.ENTRY_READY : Constants.WAIT;

        storeIndicators(
                scripCode, symbol, candleCount, Constants.FULL,
                latest.getClose(), vwap, volumeExp,
                ema20, ema50, vwapSlope, rsi, signal
        );
    }

    // ===================== STORAGE =====================
    private void storeIndicators(
            Integer scripCode, String symbol, int candleCount, String mode,
            BigDecimal price, BigDecimal vwap, BigDecimal volumeExpansion,
            BigDecimal ema20, BigDecimal ema50, BigDecimal vwapSlope, BigDecimal rsi,
            String signal) {

        Query query = new Query(Criteria.where("ScripCode").is(String.valueOf(scripCode)));
        Update update = new Update()
                .set("symbol", symbol)
                .set("timestamp", Instant.now())
                .set("candleCount", candleCount)
                .set("mode", mode)
                .set("price", price)
                .set("vwap", vwap)
                .set("volumeExpansion", volumeExpansion)
                .set("signal", signal);

        // Full scanner fields (set or clear)
        if (Constants.FULL.equals(mode)) {
            update.set("ema20", ema20);
            update.set("ema50", ema50);
            update.set("vwapSlope", vwapSlope);
            update.set("rsi", rsi);
        } else {
            update.set("ema20", null);
            update.set("ema50", null);
            update.set("vwapSlope", null);
            update.set("rsi", null);
        }

        mongoTemplate.upsert(query, update, SymbolIndicators.class);
        log.debug("Stored {} for {}: {}", mode, symbol, signal);
    }

    // ===================== UTILITIES =====================
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
}