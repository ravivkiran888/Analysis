package com.analysis.scanner;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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

    // Distance thresholds
    private static final BigDecimal MAX_DISTANCE_PERCENT = new BigDecimal("2.0");      // 2% for active levels
    private static final BigDecimal ATM_RANGE_PERCENT = new BigDecimal("1.0");         // 1% for ATM PCR
    private static final BigDecimal CLUSTER_RANGE_PERCENT = new BigDecimal("0.5");     // 0.5% for clustering
    private static final BigDecimal BREAK_PROXIMITY_PERCENT = new BigDecimal("0.3");   // 0.3% for break detection

    // Noise filter: ignore OI changes less than 0.2% of total chain OI
    private static final double MIN_OI_CHANGE_FRACTION = 0.002;

    // Weights for level selection (composite score)
    private static final double OI_WEIGHT_SELECTION = 0.6;
    private static final double PROXIMITY_WEIGHT_SELECTION = 0.4;

    // Weights for strength score (max 100)
    private static final double OI_WEIGHT_STRENGTH = 40;
    private static final double DISTANCE_WEIGHT_STRENGTH = 30;
    private static final double MOMENTUM_WEIGHT_STRENGTH = 30;

    // Scale for momentum percentage (100% buildup = 30 points)
    private static final double MOMENTUM_SCALE = 100.0;

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

    @Scheduled(cron = "0 */15 9-15 * * MON-FRI", zone = "Asia/Kolkata")
    public void scan() {
        LocalTime now = LocalTime.now(IST_ZONE);
        if (!isScanning.compareAndSet(false, true)) {
            log.warn("⚠️ Previous options scan still running at {}, skipping this execution", now);
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

        // --- Data holders ---
        List<StrikeInfo> allPuts = new ArrayList<>();
        List<StrikeInfo> allCalls = new ArrayList<>();
        List<StrikeInfo> putStrikesBelow = new ArrayList<>();
        List<StrikeInfo> callStrikesAbove = new ArrayList<>();

        long totalPutOI = 0;
        long totalCallOI = 0;
        long maxPutOI = 0;   // for side‑specific normalization
        long maxCallOI = 0;

        long atmPutOI = 0;
        long atmCallOI = 0;

        var fields = strikes.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            BigDecimal strike = new BigDecimal(entry.getKey());
            JsonNode strikeData = entry.getValue();

            // Put side
            JsonNode putNode = strikeData.path("PE");
            if (!putNode.isMissingNode() && putNode.has("open_interest")) {
                long oi = putNode.path("open_interest").asLong();
                long oiChange = putNode.path("change_in_oi").asLong(0L);
                totalPutOI += oi;
                maxPutOI = Math.max(maxPutOI, oi);
                StrikeInfo info = new StrikeInfo(strike, oi, oiChange);
                allPuts.add(info);
                if (strike.compareTo(ltp) < 0) {
                    putStrikesBelow.add(info);
                }
                if (isWithinPercent(strike, ltp, ATM_RANGE_PERCENT)) {
                    atmPutOI += oi;
                }
            }

            // Call side
            JsonNode callNode = strikeData.path("CE");
            if (!callNode.isMissingNode() && callNode.has("open_interest")) {
                long oi = callNode.path("open_interest").asLong();
                long oiChange = callNode.path("change_in_oi").asLong(0L);
                totalCallOI += oi;
                maxCallOI = Math.max(maxCallOI, oi);
                StrikeInfo info = new StrikeInfo(strike, oi, oiChange);
                allCalls.add(info);
                if (strike.compareTo(ltp) > 0) {
                    callStrikesAbove.add(info);
                }
                if (isWithinPercent(strike, ltp, ATM_RANGE_PERCENT)) {
                    atmCallOI += oi;
                }
            }
        }

        // Avoid division by zero
        if (totalPutOI == 0) totalPutOI = 1;
        if (totalCallOI == 0) totalCallOI = 1;

        // --- Cluster detection (only within MAX_DISTANCE_PERCENT) ---
        List<Cluster> supportClusters = detectClusters(putStrikesBelow, ltp, totalPutOI, true);
        List<Cluster> resistanceClusters = detectClusters(callStrikesAbove, ltp, totalCallOI, false);

        // --- Find Active Support (nearest strong wall using composite score) ---
        StrikeInfo activeSupport = findBestLevel(putStrikesBelow, ltp, maxPutOI, true);

        // --- Find Active Resistance ---
        StrikeInfo activeResistance = findBestLevel(callStrikesAbove, ltp, maxCallOI, false);

        // --- Compute strength scores (with signed momentum) ---
        int supportStrength = activeSupport != null ? computeStrengthScore(activeSupport, ltp, maxPutOI, true, totalPutOI) : 0;
        int resistanceStrength = activeResistance != null ? computeStrengthScore(activeResistance, ltp, maxCallOI, false, totalCallOI) : 0;

        // --- Compute cluster strength totals for pressure ratio ---
        double totalSupportClusterStrength = supportClusters.stream().mapToDouble(c -> c.strengthScore).sum();
        double totalResistanceClusterStrength = resistanceClusters.stream().mapToDouble(c -> c.strengthScore).sum();

        // --- Pressure ratio (support vs resistance) ---
        double pressureRatio = (supportStrength + totalSupportClusterStrength) /
                               (resistanceStrength + totalResistanceClusterStrength + 0.001); // avoid division by zero

        // --- Compute ATM PCR ---
        double atmPcr = atmCallOI == 0 ? 0 : (double) atmPutOI / atmCallOI;

        // --- Derive market bias (structural) ---
        String marketBias = deriveBias(atmPcr, supportStrength, resistanceStrength, pressureRatio);

        // --- Break / weakening flags (enhanced) ---
        boolean supportWeakening = false;
        boolean resistanceWeakening = false;
        boolean breakoutSignal = false;

        if (activeSupport != null) {
            supportWeakening = activeSupport.oiChange < 0 && isNearPrice(activeSupport.strike, ltp, BREAK_PROXIMITY_PERCENT);
        }
        if (activeResistance != null) {
            resistanceWeakening = activeResistance.oiChange < 0 && isNearPrice(activeResistance.strike, ltp, BREAK_PROXIMITY_PERCENT);
            // Breakout requires more than just weakening
            breakoutSignal = resistanceWeakening &&
                             supportStrength > resistanceStrength &&
                             atmPcr > 1.0;
        }

        // --- Build rich description ---
        String description = buildDescription(symbol, ltp, activeSupport, activeResistance,
                supportStrength, resistanceStrength, atmPcr, marketBias,
                supportWeakening, resistanceWeakening, breakoutSignal,
                supportClusters, resistanceClusters, pressureRatio);

        // --- Store everything ---
        storeLevels(symbol, ltp, activeSupport, activeResistance,
                supportStrength, resistanceStrength, atmPcr, marketBias,
                supportWeakening, resistanceWeakening, breakoutSignal,
                description, supportClusters, resistanceClusters, pressureRatio);
    }

    // Helper class for strike info
    private static class StrikeInfo {
        BigDecimal strike;
        long oi;
        long oiChange;
        StrikeInfo(BigDecimal strike, long oi, long oiChange) {
            this.strike = strike;
            this.oi = oi;
            this.oiChange = oiChange;
        }
    }

    // Helper class for clusters
    private static class Cluster {
        BigDecimal lower;
        BigDecimal upper;
        long totalOI;
        double strengthScore; // 0-100
        Cluster(BigDecimal lower, BigDecimal upper, long totalOI, double strengthScore) {
            this.lower = lower;
            this.upper = upper;
            this.totalOI = totalOI;
            this.strengthScore = strengthScore;
        }
    }

    // Check if strike is within given percent from LTP
    private boolean isWithinPercent(BigDecimal strike, BigDecimal ltp, BigDecimal percent) {
        BigDecimal diff = strike.subtract(ltp).abs();
        BigDecimal diffPct = diff.divide(ltp, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));
        return diffPct.compareTo(percent) <= 0;
    }

    // Check if price is very near a strike (for break detection)
    private boolean isNearPrice(BigDecimal strike, BigDecimal ltp, BigDecimal percent) {
        BigDecimal dist = strike.subtract(ltp).abs();
        BigDecimal distPct = dist.divide(ltp, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));
        return distPct.compareTo(percent) <= 0;
    }

    // Detect clusters of consecutive strikes with significant OI, filtered by distance
    private List<Cluster> detectClusters(List<StrikeInfo> strikes, BigDecimal ltp, long totalSideOI, boolean isSupport) {
        if (strikes.isEmpty()) return Collections.emptyList();

        // Sort by strike
        List<StrikeInfo> sorted = new ArrayList<>(strikes);
        sorted.sort(Comparator.comparing(a -> a.strike));

        List<Cluster> clusters = new ArrayList<>();
        int i = 0;
        while (i < sorted.size()) {
            // Start a potential cluster
            BigDecimal clusterLow = sorted.get(i).strike;
            BigDecimal clusterHigh = sorted.get(i).strike;
            long clusterOi = sorted.get(i).oi;
            int j = i + 1;
            while (j < sorted.size()) {
                BigDecimal prevStrike = sorted.get(j - 1).strike;
                BigDecimal currStrike = sorted.get(j).strike;
                BigDecimal gap = currStrike.subtract(prevStrike);
                BigDecimal gapPct = gap.divide(ltp, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));
                if (gapPct.compareTo(CLUSTER_RANGE_PERCENT) <= 0) {
                    // Consecutive
                    clusterHigh = currStrike;
                    clusterOi += sorted.get(j).oi;
                    j++;
                } else {
                    break;
                }
            }
            // Calculate cluster midpoint and check distance
            BigDecimal midpoint = clusterLow.add(clusterHigh).divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP);
            if (isWithinPercent(midpoint, ltp, MAX_DISTANCE_PERCENT)) {
                // Normalize cluster OI by total side OI (more stable than max OI)
                double oiNorm = (double) clusterOi / totalSideOI;
                double distPct = midpoint.subtract(ltp).abs()
                                    .divide(ltp, 4, RoundingMode.HALF_UP)
                                    .multiply(new BigDecimal("100"))
                                    .doubleValue();
                double distFactor = 1 - Math.min(1.0, distPct / MAX_DISTANCE_PERCENT.doubleValue());
                // Strength = OI contribution * OI_WEIGHT_STRENGTH + distance factor * DISTANCE_WEIGHT_STRENGTH
                double strength = (oiNorm * OI_WEIGHT_STRENGTH) + (distFactor * DISTANCE_WEIGHT_STRENGTH);
                clusters.add(new Cluster(clusterLow, clusterHigh, clusterOi, strength));
            }
            i = j;
        }
        return clusters;
    }

    // Find the best level using composite score (OI + proximity)
    private StrikeInfo findBestLevel(List<StrikeInfo> candidates, BigDecimal ltp, long maxSideOi, boolean isSupport) {
        if (candidates.isEmpty()) return null;
        StrikeInfo best = null;
        double bestScore = -1;

        for (StrikeInfo info : candidates) {
            if (!isWithinPercent(info.strike, ltp, MAX_DISTANCE_PERCENT)) continue;

            // Normalized OI (0-1) using side‑specific max
            double oiNorm = (double) info.oi / maxSideOi;
            // Proximity score: closer to LTP = higher (max 1 when distance 0)
            BigDecimal dist = isSupport ? ltp.subtract(info.strike) : info.strike.subtract(ltp);
            BigDecimal distPct = dist.divide(ltp, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));
            double proxFactor = 1 - Math.min(1.0, distPct.doubleValue() / MAX_DISTANCE_PERCENT.doubleValue());

            double composite = (oiNorm * OI_WEIGHT_SELECTION) + (proxFactor * PROXIMITY_WEIGHT_SELECTION);
            if (composite > bestScore) {
                bestScore = composite;
                best = info;
            }
        }
        return best;
    }

    // Compute strength score with signed momentum and noise filtering
    private int computeStrengthScore(StrikeInfo info, BigDecimal ltp, long maxSideOi, boolean isSupport, long totalSideOI) {
        // OI magnitude score (0-40) – normalized by side‑specific max
        double oiNorm = Math.min(1.0, (double) info.oi / maxSideOi);
        double oiScore = oiNorm * OI_WEIGHT_STRENGTH;

        // Distance score (0-30)
        BigDecimal dist = isSupport ? ltp.subtract(info.strike) : info.strike.subtract(ltp);
        BigDecimal distPct = dist.divide(ltp, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));
        double distFactor = 1 - Math.min(1.0, distPct.doubleValue() / MAX_DISTANCE_PERCENT.doubleValue());
        double distScore = distFactor * DISTANCE_WEIGHT_STRENGTH;

        // Momentum score based on percentage buildup, signed
        double momentumScore = 0;
        if (info.oi > 0 && Math.abs(info.oiChange) > MIN_OI_CHANGE_FRACTION * totalSideOI) {
            double buildupPercent = (double) info.oiChange / info.oi * 100;
            // Scale to MOMENTUM_WEIGHT_STRENGTH (max 30)
            double scaled = Math.min(MOMENTUM_WEIGHT_STRENGTH, Math.abs(buildupPercent) / MOMENTUM_SCALE * MOMENTUM_WEIGHT_STRENGTH);
            momentumScore = info.oiChange > 0 ? scaled : -scaled;
        }

        // Total score cannot be negative
        double total = oiScore + distScore + momentumScore;
        return (int) Math.max(0, total);
    }

    // Derive market bias using ATM PCR, strength comparison, and pressure ratio
    private String deriveBias(double atmPcr, int supportStrength, int resistanceStrength, double pressureRatio) {
        if (pressureRatio > 1.3 && atmPcr > 1.0) {
            return "Bullish";
        } else if (pressureRatio < 0.7 && atmPcr < 1.0) {
            return "Bearish";
        } else if (supportStrength > resistanceStrength && atmPcr > 1.0) {
            return "Bullish";
        } else if (resistanceStrength > supportStrength && atmPcr < 1.0) {
            return "Bearish";
        } else if (atmPcr > 1.2) {
            return "Bullish";
        } else if (atmPcr < 0.8) {
            return "Bearish";
        } else {
            return "Neutral";
        }
    }

    // Build human-readable description
    private String buildDescription(String symbol, BigDecimal ltp,
                                    StrikeInfo support, StrikeInfo resistance,
                                    int supportStrength, int resistanceStrength,
                                    double atmPcr, String bias,
                                    boolean supportWeakening, boolean resistanceWeakening,
                                    boolean breakoutSignal,
                                    List<Cluster> supportClusters, List<Cluster> resistanceClusters,
                                    double pressureRatio) {
        StringBuilder sb = new StringBuilder();
        sb.append(symbol).append(" @ ").append(ltp).append("\n");

        // Active support
        if (support != null) {
            BigDecimal dist = ltp.subtract(support.strike);
            BigDecimal distPct = dist.divide(ltp, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));
            String weakening = supportWeakening ? " (weakening)" : "";
            sb.append(String.format("🟢 Active Support: %.2f (%.2f%% below, OI: %,d, change: %+d)%s\n",
                    support.strike, distPct, support.oi, support.oiChange, weakening));
            sb.append("   Strength Score: ").append(supportStrength).append("/100\n");
        } else {
            sb.append("🟢 No active support within ").append(MAX_DISTANCE_PERCENT).append("%\n");
        }

        // Support clusters
        if (!supportClusters.isEmpty()) {
            sb.append("   Support Zones:\n");
            for (Cluster c : supportClusters) {
                sb.append(String.format("      %.2f-%.2f (total OI: %,d, strength: %.0f/100)\n",
                        c.lower, c.upper, c.totalOI, c.strengthScore));
            }
        }

        // Active resistance
        if (resistance != null) {
            BigDecimal dist = resistance.strike.subtract(ltp);
            BigDecimal distPct = dist.divide(ltp, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));
            String weakening = resistanceWeakening ? " (weakening)" : "";
            String breakout = breakoutSignal ? " 🚨 BREAKOUT POSSIBLE" : "";
            sb.append(String.format("🔴 Active Resistance: %.2f (%.2f%% above, OI: %,d, change: %+d)%s%s\n",
                    resistance.strike, distPct, resistance.oi, resistance.oiChange, weakening, breakout));
            sb.append("   Strength Score: ").append(resistanceStrength).append("/100\n");
        } else {
            sb.append("🔴 No active resistance within ").append(MAX_DISTANCE_PERCENT).append("%\n");
        }

        // Resistance clusters
        if (!resistanceClusters.isEmpty()) {
            sb.append("   Resistance Zones:\n");
            for (Cluster c : resistanceClusters) {
                sb.append(String.format("      %.2f-%.2f (total OI: %,d, strength: %.0f/100)\n",
                        c.lower, c.upper, c.totalOI, c.strengthScore));
            }
        }

        sb.append(String.format("📊 ATM PCR (within %.0f%%): %.2f\n", ATM_RANGE_PERCENT, atmPcr));
        sb.append(String.format("⚖️ Pressure Ratio (sup/res): %.2f\n", pressureRatio));
        sb.append("🎯 Market Bias: ").append(bias);

        return sb.toString();
    }

    // -------------------- STORAGE --------------------
    private void storeLevels(String symbol, BigDecimal ltp,
                             StrikeInfo activeSupport, StrikeInfo activeResistance,
                             int supportStrength, int resistanceStrength,
                             double atmPcr, String marketBias,
                             boolean supportWeakening, boolean resistanceWeakening,
                             boolean breakoutSignal,
                             String description,
                             List<Cluster> supportClusters, List<Cluster> resistanceClusters,
                             double pressureRatio) {

        Query query = new Query(Criteria.where("_id").is(symbol));
        Update update = new Update()
                .set("symbol", symbol)
                .set("timestamp", Instant.now())
                .set("ltp", ltp.doubleValue())
                .set("description", description)
                .set("support_strength", supportStrength)
                .set("resistance_strength", resistanceStrength)
                .set("atm_pcr", atmPcr)
                .set("market_bias", marketBias)
                .set("support_weakening", supportWeakening)
                .set("resistance_weakening", resistanceWeakening)
                .set("breakout_signal", breakoutSignal)
                .set("pressure_ratio", pressureRatio);

        // Active support
        if (activeSupport != null) {
            BigDecimal dist = ltp.subtract(activeSupport.strike);
            BigDecimal distPct = dist.divide(ltp, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));
            update.set("active_support.price", activeSupport.strike.doubleValue())
                  .set("active_support.oi", activeSupport.oi)
                  .set("active_support.oi_change", activeSupport.oiChange)
                  .set("active_support.distance_percent", distPct.doubleValue());
        } else {
            update.unset("active_support");
        }

        // Active resistance
        if (activeResistance != null) {
            BigDecimal dist = activeResistance.strike.subtract(ltp);
            BigDecimal distPct = dist.divide(ltp, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));
            update.set("active_resistance.price", activeResistance.strike.doubleValue())
                  .set("active_resistance.oi", activeResistance.oi)
                  .set("active_resistance.oi_change", activeResistance.oiChange)
                  .set("active_resistance.distance_percent", distPct.doubleValue());
        } else {
            update.unset("active_resistance");
        }

        // Support clusters
        List<org.bson.Document> supportZoneDocs = new ArrayList<>();
        for (Cluster c : supportClusters) {
            org.bson.Document doc = new org.bson.Document()
                    .append("lower", c.lower.doubleValue())
                    .append("upper", c.upper.doubleValue())
                    .append("total_oi", c.totalOI)
                    .append("strength", c.strengthScore);
            supportZoneDocs.add(doc);
        }
        update.set("support_zones", supportZoneDocs);

        // Resistance clusters
        List<org.bson.Document> resistanceZoneDocs = new ArrayList<>();
        for (Cluster c : resistanceClusters) {
            org.bson.Document doc = new org.bson.Document()
                    .append("lower", c.lower.doubleValue())
                    .append("upper", c.upper.doubleValue())
                    .append("total_oi", c.totalOI)
                    .append("strength", c.strengthScore);
            resistanceZoneDocs.add(doc);
        }
        update.set("resistance_zones", resistanceZoneDocs);

        mongoTemplate.upsert(query, update, "stock_levels");
        log.debug("Stored levels for {}: LTP={}, support={}, resistance={}, bias={}, breakout={}",
                symbol, ltp,
                activeSupport != null ? activeSupport.strike : "none",
                activeResistance != null ? activeResistance.strike : "none",
                marketBias, breakoutSignal);
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