package com.analysis.scanner;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import org.springframework.scheduling.annotation.Scheduled;
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
    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");
    private static final BigDecimal PROXIMITY_THRESHOLD = new BigDecimal("0.5");      // ₹0.50 for key support proximity
    private static final BigDecimal IMMEDIATE_SUPPORT_PERCENT = new BigDecimal("0.05"); // 5% below LTP

    private final OptionsAPIClient optionsApiClient;
    private final MongoTemplate mongoTemplate;
    private final ObjectMapper objectMapper;
    private final OptionSymbolRepository optionSymbolRepository;
    private final ExecutorService executor;
    private final AtomicBoolean isScanning = new AtomicBoolean(false);
    private volatile LocalTime lastScanStartTime = null;

    public OptionsLevelScanner(
            MongoTemplate mongoTemplate,
            OptionsAPIClient optionsApiClient,
            OptionSymbolRepository optionSymbolRepository) {
        this.mongoTemplate = mongoTemplate;
        this.optionsApiClient = optionsApiClient;
        this.objectMapper = new ObjectMapper();
        this.optionSymbolRepository = optionSymbolRepository;
        this.executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        log.info("Initialized OptionsLevelScanner with thread pool of {} threads", THREAD_POOL_SIZE);
    }

  
    @Scheduled(cron = "0 */15 9-15 * * MON-FRI")
    public void scan() {
        LocalTime now = LocalTime.now(IST_ZONE);
        // Skip if after 3:30 PM (15:30)
      

        if (!isScanning.compareAndSet(false, true)) {
            log.warn("⚠️ Previous scan still running at {}, skipping this execution", now);
            return;
        }
        lastScanStartTime = now;
        try {
            executeScan(now);
        } catch (Exception e) {
            log.error("Fatal error during options level scan", e);
        } finally {
            isScanning.set(false);
            log.info("Options level scan lock released at {}", LocalTime.now(IST_ZONE));
        }
    }

    private void executeScan(LocalTime startTime) {
        log.info("🔍 Starting options level scan at {} IST", startTime);
        List<OptionSymbol> allOptionSymbols = optionSymbolRepository.findAll();

        // Extract symbols only
        List<String> symbols = allOptionSymbols.stream()
                .map(OptionSymbol::getSymbol)
                .collect(Collectors.toList());

        CountDownLatch latch = new CountDownLatch(symbols.size());
        AtomicInteger processed = new AtomicInteger(0);
        AtomicInteger successful = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);

        for (String symbol : symbols) {
            if (shouldAbortScan(startTime)) {
                log.warn("⚠️ Aborting options scan at {} IST to prevent overlap", LocalTime.now(IST_ZONE));
                break;
            }
            executor.submit(() -> {
                try {
                    Constants.RATE_LIMITER.acquire();
                    processSymbol(symbol);
                    successful.incrementAndGet();
                } catch (Exception e) {
                    failed.incrementAndGet();
                    log.error("Error processing {}: {}", symbol, e.getMessage());
                } finally {
                    processed.incrementAndGet();
                    latch.countDown();
                }
            });
        }

        try {
            boolean completed = latch.await(SCAN_TIMEOUT_MINUTES, TimeUnit.MINUTES);
            if (!completed) {
                log.warn("⚠️ Options scan timed out after {} minutes. Completed: {}/{}",
                        SCAN_TIMEOUT_MINUTES, processed.get(), symbols.size());
            } else {
                log.info("✅ Options scan completed in {} ms at {} IST (Success: {}, Failed: {})",
                        System.currentTimeMillis() - startTime.toNanoOfDay() / 1_000_000,
                        LocalTime.now(IST_ZONE), successful.get(), failed.get());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Options scan was interrupted", e);
        }
    }

    private boolean shouldAbortScan(LocalTime startTime) {
        long elapsedSeconds = java.time.Duration.between(startTime, LocalTime.now(IST_ZONE)).getSeconds();
        return elapsedSeconds > 240; // 4 minutes
    }

    // -------------------- PER‑SYMBOL PROCESSING --------------------
    private void processSymbol(String symbol) throws Exception {
        String json = optionsApiClient.getOptionsChain(symbol);
        if (json == null) {
            log.debug("Skipping {} – no options data", symbol);
            return;
        }

        JsonNode root = objectMapper.readTree(json);
        String status = root.path("status").asText();
        if (!"SUCCESS".equalsIgnoreCase(status)) {
            log.warn("Options API returned non‑success for {}: {}", symbol, status);
            return;
        }

        JsonNode payload = root.path("payload");
        BigDecimal ltp = payload.path("underlying_ltp").decimalValue();
        JsonNode strikes = payload.path("strikes");

        // --- Initialize totals for PCR ---
        long totalPutOI = 0;
        long totalCallOI = 0;

        // --- Compute key support (max put OI) ---
        BigDecimal keySupportPrice = null;
        long maxPutOI = 0;
        long keySupportOI = 0;

        // --- Compute resistance (max call OI) ---
        BigDecimal resistancePrice = null;
        long maxCallOI = 0;
        long resistanceOI = 0;
        long resistanceVolume = 0;

        // Collect put OI for immediate support calculation
        List<StrikeInfo> putStrikesBelow = new ArrayList<>();

        var fields = strikes.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            BigDecimal strikePrice = new BigDecimal(entry.getKey());
            JsonNode strikeData = entry.getValue();

            // Put side
            JsonNode putNode = strikeData.path("PE");
            if (!putNode.isMissingNode() && putNode.has("open_interest")) {
                long putOI = putNode.path("open_interest").asLong();
                totalPutOI += putOI;
                if (putOI > maxPutOI) {
                    maxPutOI = putOI;
                    keySupportPrice = strikePrice;
                    keySupportOI = putOI;
                }
                if (strikePrice.compareTo(ltp) < 0) {
                    putStrikesBelow.add(new StrikeInfo(strikePrice, putOI));
                }
            }

            // Call side
            JsonNode callNode = strikeData.path("CE");
            if (!callNode.isMissingNode() && callNode.has("open_interest")) {
                long callOI = callNode.path("open_interest").asLong();
                totalCallOI += callOI;
                if (callOI > maxCallOI) {
                    maxCallOI = callOI;
                    resistancePrice = strikePrice;
                    resistanceOI = callOI;
                    resistanceVolume = callNode.path("volume").asLong();
                }
            }
        }

        // --- Compute immediate support zone ---
        BigDecimal immLow = null;
        BigDecimal immHigh = null;
        Map<BigDecimal, Long> zoneOIs = new HashMap<>();

        if (!putStrikesBelow.isEmpty()) {
            BigDecimal lowerBound = ltp.multiply(BigDecimal.ONE.subtract(IMMEDIATE_SUPPORT_PERCENT));
            List<StrikeInfo> candidates = putStrikesBelow.stream()
                    .filter(s -> s.strike.compareTo(lowerBound) >= 0)
                    .collect(Collectors.toList());

            if (!candidates.isEmpty()) {
                StrikeInfo best = candidates.stream()
                        .max((a, b) -> Long.compare(a.oi, b.oi))
                        .get();
                immLow = best.strike;
                immHigh = best.strike;
                zoneOIs.put(best.strike, best.oi);

                BigDecimal[] highRef = { best.strike };
                BigDecimal nextStrike = best.strike.add(BigDecimal.ONE);
                candidates.stream()
                        .filter(s -> s.strike.compareTo(nextStrike) == 0)
                        .findFirst()
                        .ifPresent(next -> {
                            if (next.oi >= best.oi / 2) {
                                highRef[0] = next.strike;
                                zoneOIs.put(next.strike, next.oi);
                            }
                        });
                immHigh = highRef[0];
            }
        }

        // Fallback if no immediate support found
        if (immLow == null) {
            immLow = keySupportPrice;
            immHigh = keySupportPrice;
            if (keySupportPrice != null) {
                zoneOIs.put(keySupportPrice, keySupportOI);
            }
        }

        // --- Compute Put‑Call Ratio (OI) ---
        double pcr = totalCallOI == 0 ? 0 : (double) totalPutOI / totalCallOI;
        String pcrFormatted = String.format("%.2f", pcr);

        // --- Build rich description ---
        StringBuilder desc = new StringBuilder();
        desc.append(symbol).append(" @ ").append(ltp).append("\n");
        if (keySupportPrice != null) {
            desc.append("- Key Support: ").append(keySupportPrice)
                .append(" (OI: ").append(String.format("%,d", keySupportOI)).append(")\n");
        }
        if (immLow != null && immHigh != null) {
            desc.append("- Immediate Support Zone: ").append(immLow);
            if (immLow.compareTo(immHigh) != 0) {
                desc.append(" - ").append(immHigh);
            }
            desc.append(" (OI: ");
            boolean first = true;
            for (Map.Entry<BigDecimal, Long> e : zoneOIs.entrySet()) {
                if (!first) desc.append(", ");
                desc.append(e.getKey()).append(": ").append(String.format("%,d", e.getValue()));
                first = false;
            }
            desc.append(")\n");
        }
        if (resistancePrice != null) {
            desc.append("- Resistance: ").append(resistancePrice)
                .append(" (OI: ").append(String.format("%,d", resistanceOI))
                .append(", Vol: ").append(String.format("%,d", resistanceVolume)).append(")\n");
        }
        desc.append("- PCR (OI): ").append(pcrFormatted).append("\n");
        desc.append("- Total Put OI: ").append(String.format("%,d", totalPutOI))
            .append(" | Total Call OI: ").append(String.format("%,d", totalCallOI));

        String description = desc.toString();

        // --- Store the levels with description ---
        storeLevels(symbol, ltp,
                keySupportPrice, keySupportOI,
                immLow, immHigh, zoneOIs,
                resistancePrice, resistanceOI, resistanceVolume,
                description);
    }

    // Helper class for strike info
    private static class StrikeInfo {
        BigDecimal strike;
        long oi;
        StrikeInfo(BigDecimal strike, long oi) { this.strike = strike; this.oi = oi; }
    }

    // -------------------- STORAGE --------------------
    private void storeLevels(String symbol, BigDecimal ltp,
                             BigDecimal keySupportPrice, long keySupportOI,
                             BigDecimal immLow, BigDecimal immHigh,
                             Map<BigDecimal, Long> zoneOIs,
                             BigDecimal resistancePrice, long resistanceOI, long resistanceVolume,
                             String description) {

        Query query = new Query(Criteria.where("_id").is(symbol));
        Update update = new Update()
                .set("symbol", symbol)
                .set("timestamp", Instant.now())
                .set("ltp", ltp.doubleValue())
                .set("levels.key_support.price", keySupportPrice != null ? keySupportPrice.doubleValue() : null)
                .set("levels.key_support.put_oi", keySupportOI)
                .set("levels.immediate_support.lower", immLow != null ? immLow.doubleValue() : null)
                .set("levels.immediate_support.upper", immHigh != null ? immHigh.doubleValue() : null)
                .set("levels.resistance.price", resistancePrice != null ? resistancePrice.doubleValue() : null)
                .set("levels.resistance.call_oi", resistanceOI)
                .set("levels.resistance.call_volume", resistanceVolume)
                .set("description", description);          // New field for the rich description

        if (!zoneOIs.isEmpty()) {
            org.bson.Document zoneOIDoc = new org.bson.Document();
            zoneOIs.forEach((strike, oi) -> zoneOIDoc.append(strike.toString(), oi));
            update.set("levels.immediate_support.put_oi_range", zoneOIDoc);
        }

        mongoTemplate.upsert(query, update, "stock_levels");
        log.debug("Stored levels for {}: LTP={}, keySupport={} (OI={}), imm=[{}-{}], resistance={} (OI={}, vol={})",
                symbol, ltp, keySupportPrice, keySupportOI, immLow, immHigh, resistancePrice, resistanceOI, resistanceVolume);
    }

    // -------------------- CLEANUP --------------------
    @PreDestroy
    public void cleanup() {
        log.info("Shutting down OptionsLevelScanner thread pool...");
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                    log.error("OptionsLevelScanner thread pool did not terminate");
                }
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("OptionsLevelScanner thread pool shut down successfully");
    }
}