package com.analysis.jobs;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.analysis.apicalls.MarketSnapshotHttpClient;
import com.analysis.constants.Constants;
import com.analysis.documents.ScripMaster;
import com.analysis.documents.SymbolIndicators;
import com.analysis.dto.MarketSnapshotRequest;
import com.analysis.dto.MarketSnapshotResponse;
import com.analysis.repository.ScripMasterRepository;
import com.analysis.service.AccessTokenService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class MarketSnapshotJob {

    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");
    private static final int BATCH_SIZE = 3;  // Your proven batch size
    private static final int BATCH_DELAY_MS = 1000; // 1 second between batches
    private static final int THREAD_POOL_SIZE = 5;
    
    private final ScripMasterRepository scripMasterRepository;
    private final MarketSnapshotHttpClient httpClient;
    private final MongoTemplate mongoTemplate;
    private final AccessTokenService accessTokenService;
    
    private final ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

    public MarketSnapshotJob(
            ScripMasterRepository scripMasterRepository,
            MarketSnapshotHttpClient httpClient,
            MongoTemplate mongoTemplate,
            AccessTokenService accessTokenService) {
        this.scripMasterRepository = scripMasterRepository;
        this.httpClient = httpClient;
        this.mongoTemplate = mongoTemplate;
        this.accessTokenService = accessTokenService;
    }

    public void run() {
        LocalTime startTime = LocalTime.now(IST_ZONE);
        log.info("📊 MarketSnapshot Job started at {}", startTime);

        // Check if market is likely open
        if (!isMarketOpen(startTime)) {
            log.warn("Market may be closed. Current time: {}. Job may return no data.", startTime);
        }

        String accessToken = accessTokenService.getAccessToken();
        if (!StringUtils.hasText(accessToken)) {
            log.error("❌ Access token missing");
            return;
        }

        List<ScripMaster> allScripts = scripMasterRepository.findAll();
        
        if (allScripts.isEmpty()) {
            log.error("No scrips to process");
            return;
        }
        
        log.info("Processing {} scrips in batches of {}", allScripts.size(), BATCH_SIZE);

        // Create batches
        List<List<ScripMaster>> batches = new ArrayList<>();
        for (int i = 0; i < allScripts.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, allScripts.size());
            batches.add(allScripts.subList(i, end));
        }

        CountDownLatch latch = new CountDownLatch(batches.size());
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failedBatches = new AtomicInteger();

        // Process batches with delay between them
        for (int i = 0; i < batches.size(); i++) {
            final int batchIndex = i;
            List<ScripMaster> batch = batches.get(i);
            
            executorService.submit(() -> {
                try {
                    // Delay between batches
                    if (batchIndex > 0) {
                        Thread.sleep(BATCH_DELAY_MS);
                    }
                    
                    boolean batchSuccess = processBatch(batch);
                    
                    if (batchSuccess) {
                        successCount.addAndGet(batch.size());
                    } else {
                        failedBatches.incrementAndGet();
                        // Retry failed batch individually
                        retryBatchIndividually(batch);
                    }
                    
                } catch (Exception e) {
                    failedBatches.incrementAndGet();
                    log.error("Batch {} failed: {}", batchIndex + 1, e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        // Wait for completion
        try {
            boolean completed = latch.await(15, TimeUnit.MINUTES);
            if (!completed) {
                log.warn("Timeout - {} batches pending", latch.getCount());
            }
            
            int totalSaved = successCount.get();
            log.info("✅ Job completed. Success: {}/{} ({}% success rate)", 
                     totalSaved, allScripts.size(),
                     allScripts.size() > 0 ? (totalSaved * 100) / allScripts.size() : 0);
            
            if (failedBatches.get() > 0) {
                log.warn("{} batches had failures (retried individually)", failedBatches.get());
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Job interrupted");
        }
    }

    private boolean processBatch(List<ScripMaster> batch) {
        try {
            // Create batch requests using your DTO
        	
        	List<MarketSnapshotRequest> requests = batch.stream()
        	        .map(scrip -> MarketSnapshotRequest.builder()
        	                .exchange("N")
        	                .exchangeType("C")
        	                .scripCode(Long.valueOf(scrip.getScripCode()))
        	                .symbol(scrip.getSymbol())
        	                .build()
        	        )
        	        .toList();   
            
            // Fetch market snapshot
            MarketSnapshotResponse response = httpClient.fetchSnapshot(requests);

            if (response == null || response.getBody() == null) {
                log.error("Null response for batch of {} scrips", batch.size());
                return false;
            }

            // Check response status
            if (response.getBody().getStatus() != 0) {
                log.warn("API returned error status: {} - {}", 
                         response.getBody().getStatus(),
                         response.getBody().getMessage());
                return false;
            }

            if (response.getBody().getData() == null || 
                response.getBody().getData().isEmpty()) {
                log.warn("Empty data in response for batch");
                return false;
            }

            // Process each data item - update symbol_indicators
            int savedCount = 0;
            for (MarketSnapshotResponse.MarketSnapshotData data : response.getBody().getData()) {
                boolean saved = enrichSymbolIndicators(data);
                if (saved) {
                    savedCount++;
                }
            }
            
            log.info("Batch processed: {}/{} successful", savedCount, batch.size());
            return savedCount > 0;
            
        } catch (Exception e) {
            log.error("Batch processing failed: {}", e.getMessage());
            return false;
        }
    }
    
    private void retryBatchIndividually(List<ScripMaster> batch) {
        log.info("Retrying batch individually ({} scrips)", batch.size());
        
        int individualSuccess = 0;
        for (ScripMaster scrip : batch) {
            try {
                Thread.sleep(200); // Small delay between retries
                
                boolean success = processSingleScrip(scrip);
                if (success) {
                    individualSuccess++;
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Individual retry failed for {}: {}", 
                         scrip.getSymbol(), e.getMessage());
            }
        }
        
        log.info("Individual retry result: {}/{} successful", individualSuccess, batch.size());
    }
    
    private boolean processSingleScrip(ScripMaster scrip) {
        try {
           
        String scripCode = 	scrip.getScripCode();
        String symbol = scrip.getSymbol();
        	
        	 MarketSnapshotRequest request =
     	            MarketSnapshotRequest.builder()
     	                    .exchange("N")
     	                    .exchangeType("C")
     	                    .scripCode(Long.valueOf(scripCode))
     	                    .symbol(symbol)
     	                    .build();
            
            MarketSnapshotResponse response = httpClient.fetchSnapshot(List.of(request));
            
            if (response == null || response.getBody() == null || 
                response.getBody().getData() == null || 
                response.getBody().getData().isEmpty()) {
                log.error("No data for individual scrip {}", scrip.getSymbol());
                return false;
            }
            
            MarketSnapshotResponse.MarketSnapshotData data = response.getBody().getData().get(0);
            return enrichSymbolIndicators(data);
            
        } catch (Exception e) {
            log.error("Individual scrip processing failed for {}: {}", 
                     scrip.getSymbol(), e.getMessage());
            return false;
        }
    }

    private boolean enrichSymbolIndicators(MarketSnapshotResponse.MarketSnapshotData data) {
        try {
            String scripCode = String.valueOf(data.getScripCode());
            
            Query query = new Query(Criteria.where("scripCode").is(scripCode));
            Update update = new Update()
                    .set("dayHigh", parseBigDecimal(data.getHigh()))
                    .set("dayLow", parseBigDecimal(data.getLow()))
                    .set("dayOpen", parseBigDecimal(data.getOpen()))
                    .set("lastTradedPrice", parseBigDecimal(data.getLastTradedPrice()))
                    .set("dayChange", parseBigDecimal(data.getNetChange()))
                    .set(Constants.TOTAL_DAY_VOLUME, parseLong(data.getVolume()))
                    .set("snapshotTimestamp", Instant.now());

            var result = mongoTemplate.updateFirst(query, update, SymbolIndicators.class);
            
            if (result.getMatchedCount() == 0) {
                log.debug("No symbol_indicators document found for scripCode {}, skipping...", scripCode);
                return false;
            }
            
            log.debug("Updated {}: High={}, Low={}, LTP={}, Change={}", 
                scripCode, data.getHigh(), data.getLow(), 
                data.getLastTradedPrice(), data.getNetChange());
            
            return true;

        } catch (Exception e) {
            log.error("Failed to enrich symbol_indicators for scripCode {}: {}", 
                data.getScripCode(), e.getMessage());
            return false;
        }
    }

    private BigDecimal parseBigDecimal(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long parseLong(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean isMarketOpen(LocalTime time) {
        int hour = time.getHour();
        int minute = time.getMinute();
        
        // Market hours: 9:15 AM to 3:30 PM
        if (hour < 9 || hour > 15) return false;
        if (hour == 9 && minute < 15) return false;
        if (hour == 15 && minute > 30) return false;
        
        // Weekend check
        java.time.DayOfWeek day = java.time.LocalDate.now().getDayOfWeek();
        return day != java.time.DayOfWeek.SATURDAY && day != java.time.DayOfWeek.SUNDAY;
    }

    @jakarta.annotation.PreDestroy
    public void cleanup() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}