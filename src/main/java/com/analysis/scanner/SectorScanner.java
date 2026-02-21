package com.analysis.scanner;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.analysis.apicalls.SectorAPIClient;
import com.analysis.constants.Constants;
import com.analysis.documents.SectorIndices;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class SectorScanner {

    // ===================== CONSTANTS =====================
    private static final int THREAD_POOL_SIZE = 4;
    private static final int SCAN_TIMEOUT_MINUTES = 2;
    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");
    private static final String COLLECTION_NAME = "sector_indicators";

    private final MongoTemplate mongoTemplate;
    private final SectorAPIClient sectorAPIClient;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor;
    private final AtomicBoolean isScanning = new AtomicBoolean(false);
    private volatile LocalTime lastScanStartTime = null;

    public SectorScanner(
            MongoTemplate mongoTemplate,
            SectorAPIClient sectorAPIClient) {
        this.mongoTemplate = mongoTemplate;
        this.sectorAPIClient = sectorAPIClient;
        this.objectMapper = new ObjectMapper();
        this.executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        log.info("Initialized SectorScanner thread pool with {} threads", THREAD_POOL_SIZE);
    }

    // -------------------- PUBLIC SCAN METHOD --------------------
    
    @Scheduled(cron = "0 */5 9-14 * * MON-FRI", zone = "Asia/Kolkata")
    @Scheduled(cron = "0 0-30/5 15 * * MON-FRI", zone = "Asia/Kolkata")
    
    public void scan() {
        LocalTime now = LocalTime.now(IST_ZONE);
        if (!isScanning.compareAndSet(false, true)) {
            log.warn("⚠️ Previous sector scan still running at {}, skipping this execution", now);
            return;
        }
        lastScanStartTime = now;
        try {
            executeScan();
        } catch (Exception e) {
            log.error("Fatal error during sector scan", e);
        } finally {
            isScanning.set(false);
            log.info("Sector scan lock released at {}", LocalTime.now(IST_ZONE));
        }
    }

    private void executeScan() {
        log.info("🔍 Starting sector scan at {} IST", LocalTime.now(IST_ZONE));
        
        // Fetch all sector indices from the collection
        List<SectorIndices> sectorIndices = mongoTemplate.findAll(SectorIndices.class);
        
        if (sectorIndices.isEmpty()) {
            log.warn("No sector indices found in database");
            return;
        }

        CountDownLatch latch = new CountDownLatch(sectorIndices.size());
        AtomicInteger processed = new AtomicInteger(0);
        AtomicInteger successful = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);

        for (SectorIndices sector : sectorIndices) {
            executor.submit(() -> {
                try {
                    Constants.RATE_LIMITER.acquire();
                    processSector(sector);
                    successful.incrementAndGet();
                } catch (Exception e) {
                    failed.incrementAndGet();
                    log.error("Error processing {}: {}", sector.getSector(), e.getMessage());
                } finally {
                    processed.incrementAndGet();
                    latch.countDown();
                }
            });
        }

        try {
            boolean completed = latch.await(SCAN_TIMEOUT_MINUTES, TimeUnit.MINUTES);
            if (!completed) {
                log.warn("⚠️ Sector scan timed out after {} minutes. Completed: {}/{}",
                        SCAN_TIMEOUT_MINUTES, processed.get(), sectorIndices.size());
            } else {
                log.info("✅ Sector scan completed at {} IST (Success: {}, Failed: {})",
                        LocalTime.now(IST_ZONE), successful.get(), failed.get());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Sector scan was interrupted", e);
        }
    }

    // -------------------- PROCESS SINGLE SECTOR --------------------
    private void processSector(SectorIndices sector) throws Exception {
        String tradingSymbol = sector.getSector(); // e.g., "BANKNIFTY"
        
        log.debug("Fetching data for {}", tradingSymbol);

        // Make API call using SectorAPIClient
        String responseBody = sectorAPIClient.getSectorData(tradingSymbol);
        
        if (responseBody != null) {
            parseAndUpdate(sector, responseBody);
        } else {
            log.error("Failed to fetch data for {}: null response", tradingSymbol);
        }
    }

    // -------------------- PARSE AND UPDATE --------------------
    private void parseAndUpdate(SectorIndices sector, String responseBody) throws Exception {
        JsonNode rootNode = objectMapper.readTree(responseBody);
        
        // Check status
        String status = rootNode.path("status").asText();
        if (!"SUCCESS".equals(status)) {
            log.error("API returned non-success status for {}: {}", sector.getSector(), status);
            return;
        }

        JsonNode payload = rootNode.path("payload");
        
        // Extract day_change and day_change_perc
        BigDecimal dayChange = extractBigDecimal(payload, "day_change");
        BigDecimal dayChangePercent = extractBigDecimal(payload, "day_change_perc");
        
        if (dayChange == null) {
            log.warn("No day_change data for {}", sector.getSector());
            return;
        }

        // UPDATE based on name field
        updateSectorData(
            sector.getSector(),
            sector.getName(),
            dayChange,
            dayChangePercent
        );

        log.info("✅ Updated {} - Change: {} ({}%) at {}", 
            sector.getSector(), 
            dayChange, 
            dayChangePercent != null ? dayChangePercent.setScale(2, BigDecimal.ROUND_HALF_UP) : "N/A",
            LocalTime.now(IST_ZONE)
        );
    }

    // -------------------- HELPER: Extract BigDecimal safely --------------------
    private BigDecimal extractBigDecimal(JsonNode node, String field) {
        if (node.has(field) && !node.path(field).isNull()) {
            return node.path(field).decimalValue();
        }
        return null;
    }

    // -------------------- UPDATE BASED ON NAME --------------------
    private void updateSectorData(
            String sector,
            String name,
            BigDecimal dayChange,
            BigDecimal dayChangePercent) {

        // Create query based on name field
        Query query = new Query(Criteria.where("name").is(name));
        
        // Create update object
        Update update = new Update()
                .set("sector", sector)
                .set("name", name)
                .set("timestamp", Instant.now())
                .set("dayChange", dayChange)
                .set("dayChangePercent", dayChangePercent);

        // Update the document (insert if not exists, update if exists)
        mongoTemplate.upsert(query, update, COLLECTION_NAME);
    }

    // -------------------- UTILITIES --------------------
    public boolean isScanning() { 
        return isScanning.get(); 
    }
    
    public LocalTime getLastScanStartTime() { 
        return lastScanStartTime; 
    }

    @PreDestroy
    public void cleanup() {
        log.info("Shutting down SectorScanner thread pool...");
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                    log.error("SectorScanner thread pool did not terminate");
                }
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("SectorScanner thread pool shut down successfully");
    }
}