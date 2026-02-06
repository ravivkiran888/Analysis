package com.analysis.jobs;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.analysis.documents.MarketSnapshot;
import com.analysis.dto.MarketSnapshotRequest;
import com.analysis.dto.MarketSnapshotResponse;
import com.analysis.dto.ScripInfo;
import com.analysis.helpers.MarketSnapshotHttpClient;
import com.analysis.repositories.MarketSnapshotRepository;
import com.analysis.services.AccessTokenService;
import com.analysis.services.ScripCache;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MarketSnapshotJob {

    private static final Logger log = LoggerFactory.getLogger(MarketSnapshotJob.class);

    private static final int BATCH_SIZE = 3;  // Changed from 10 to 3
    private static final int THREAD_POOL_SIZE = 5;
    
    private final ScripCache scripCache;
    private final MarketSnapshotHttpClient httpClient;
    private final MarketSnapshotRepository repository;
    private final AccessTokenService accessTokenService;

    private final ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

    public void run() {
        log.info("MarketSnapshot Job started");

        String accessToken = accessTokenService.getAccessToken();
        if (!StringUtils.hasText(accessToken)) {
            log.error("Access token missing");
            return;
        }

        List<Map.Entry<Integer, ScripInfo>> scripList = new ArrayList<>();
        scripCache.getAllScripEntries().forEach(scripList::add);

        int totalScrips = scripList.size();
        
        if (totalScrips == 0) {
            log.error("No scrips to process");
            return;
        }
        
        log.info("Processing {} scrips in batches of {}", totalScrips, BATCH_SIZE);

        // Create batches
        List<List<Map.Entry<Integer, ScripInfo>>> batches = new ArrayList<>();
        for (int i = 0; i < scripList.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, scripList.size());
            batches.add(scripList.subList(i, end));
        }

        CountDownLatch latch = new CountDownLatch(batches.size());
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failedBatches = new AtomicInteger();

        // Process batches with 1 second delay between them
        for (int i = 0; i < batches.size(); i++) {
            final int batchIndex = i;
            List<Map.Entry<Integer, ScripInfo>> batch = batches.get(i);
            
            executorService.submit(() -> {
                try {
                    // 1 second delay between batches (3 requests/sec - very safe)
                    if (batchIndex > 0) {
                        Thread.sleep(1000);
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
            boolean completed = latch.await(15, TimeUnit.MINUTES); // Increased timeout
            if (!completed) {
                log.warn("Timeout - {} batches pending", latch.getCount());
            }
            
            int totalSaved = successCount.get();
            log.info("Job completed. Success: {}/{} ({}% success rate)", 
                     totalSaved, totalScrips,
                     totalScrips > 0 ? (totalSaved * 100) / totalScrips : 0);
            
            if (failedBatches.get() > 0) {
                log.warn("{} batches had failures (retried individually)", failedBatches.get());
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Job interrupted");
        }
    }

    private boolean processBatch(List<Map.Entry<Integer, ScripInfo>> batch) {
        try {
            // Create batch request
            List<MarketSnapshotRequest> requests = batch.stream()
                .map(entry -> new MarketSnapshotRequest(
                    "N",
                    "C",
                    entry.getKey(),
                    entry.getValue().getSymbol()
                ))
                .collect(Collectors.toList());
            
            // Fetch market snapshot
            MarketSnapshotResponse response = httpClient.fetchSnapshot(requests);

            if (response == null || response.getBody() == null) {
                log.error("Null response for batch of {} scrips", batch.size());
                return false;
            }

            if (response.getBody().getData() == null || 
                response.getBody().getData().isEmpty()) {
                log.warn("Empty data in response for batch. Status: {}, Message: {}", 
                         response.getBody().getStatus(),
                         response.getBody().getMessage());
                return false;
            }

            // Verify we got data for all requested scrips
            int receivedCount = response.getBody().getData().size();
            if (receivedCount < batch.size()) {
                log.warn("Batch partial: received {}/{} scrips", receivedCount, batch.size());
            }

            // Process each data item
            int savedCount = 0;
            for (MarketSnapshotResponse.MarketSnapshotData data : response.getBody().getData()) {
                // Find matching symbol
                String symbol = batch.stream()
                    .filter(e -> e.getKey().equals(data.getScripCode().intValue()))
                    .map(e -> e.getValue().getSymbol())
                    .findFirst()
                    .orElse(null);
                    
                if (symbol != null) {
                    boolean saved = saveMarketSnapshot(data, symbol, Instant.now());
                    if (saved) {
                        savedCount++;
                    }
                }
            }
            
            return savedCount > 0;
            
        } catch (Exception e) {
            log.error("Batch processing failed: {}", e.getMessage());
            return false;
        }
    }
    
    private void retryBatchIndividually(List<Map.Entry<Integer, ScripInfo>> batch) {
        log.info("Retrying batch individually ({} scrips)", batch.size());
        
        int individualSuccess = 0;
        for (Map.Entry<Integer, ScripInfo> entry : batch) {
            try {
                // Small delay between individual retries
                Thread.sleep(200);
                
                boolean success = processSingleScrip(entry.getKey(), entry.getValue());
                if (success) {
                    individualSuccess++;
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Individual retry failed for {}: {}", 
                         entry.getValue().getSymbol(), e.getMessage());
            }
        }
        
        log.info("Individual retry result: {}/{} successful", individualSuccess, batch.size());
    }
    
    private boolean processSingleScrip(int scripCode, ScripInfo scripInfo) {
        try {
            MarketSnapshotRequest request = new MarketSnapshotRequest(
                "N", "C", scripCode, scripInfo.getSymbol()
            );
            
            MarketSnapshotResponse response = httpClient.fetchSnapshot(List.of(request));
            
            if (response == null || response.getBody() == null || 
                response.getBody().getData() == null || 
                response.getBody().getData().isEmpty()) {
                log.error("No data for individual scrip {}", scripInfo.getSymbol());
                return false;
            }
            
            MarketSnapshotResponse.MarketSnapshotData data = response.getBody().getData().get(0);
            return saveMarketSnapshot(data, scripInfo.getSymbol(), Instant.now());
            
        } catch (Exception e) {
            log.error("Individual scrip processing failed for {}: {}", 
                     scripInfo.getSymbol(), e.getMessage());
            return false;
        }
    }

    private boolean saveMarketSnapshot(
            MarketSnapshotResponse.MarketSnapshotData data,
            String symbol,
            Instant capturedAt) {

        try {
            String scripCodeStr = String.valueOf(data.getScripCode());
            
            MarketSnapshot existing = repository.findByScripCode(scripCodeStr);
            Instant now = Instant.now();

            MarketSnapshot snapshot;
            
            if (existing != null) {
                snapshot = updateFromResponse(existing, data);
                snapshot.setUpdatedAt(now);
            } else {
                snapshot = mapToEntity(data, symbol);
                snapshot.setCreatedAt(now);
                snapshot.setUpdatedAt(now);
            }
            
            snapshot.setCapturedAt(capturedAt);
            repository.save(snapshot);
            
            log.debug("Saved {}: LastPrice={}, NetChange={}, Volume={}", 
                     symbol, 
                     snapshot.getLastTradedPrice(),
                     snapshot.getNetChange(),
                     snapshot.getVolume());
            
            return true;
            
        } catch (Exception e) {
            log.error("Failed to save snapshot for {}: {}", symbol, e.getMessage());
            return false;
        }
    }

    
    private MarketSnapshot mapToEntity(
            MarketSnapshotResponse.MarketSnapshotData data,
            String symbol) {

        MarketSnapshot snapshot = new MarketSnapshot();
        
        // Basic identifiers
        snapshot.setScripCode(String.valueOf(data.getScripCode()));
        snapshot.setSymbol(symbol);
        snapshot.setExchange(data.getExchange());
        snapshot.setExchangeType(data.getExchangeType());
        
        // Price data
        snapshot.setLastTradedPrice(parseDouble(data.getLastTradedPrice()));
        snapshot.setNetChange(parseDouble(data.getNetChange()));
        snapshot.setPClose(parseDouble(data.getPClose()));
        snapshot.setOpen(parseDouble(data.getOpen()));
        snapshot.setHigh(parseDouble(data.getHigh()));
        snapshot.setLow(parseDouble(data.getLow()));
        snapshot.setClose(parseDouble(data.getPClose())); // Using PClose as close
        
        // Volume and quantity
        snapshot.setVolume(parseDouble(data.getVolume()));
        snapshot.setTotalBuyQuantity(parseDouble(data.getTotalBuyQuantity()));
        snapshot.setTotalSellQuantity(parseDouble(data.getTotalSellQuantity()));
        snapshot.setBuyQuantity(data.getBuyQuantity());
        snapshot.setSellQuantity(data.getSellQuantity());
        snapshot.setLastQuantity(data.getLastQuantity());
        
        // Additional price levels
        snapshot.setAHigh(parseDouble(data.getAHigh()));
        snapshot.setALow(parseDouble(data.getALow()));
        snapshot.setLowerCircuitLimit(parseDouble(data.getLowerCircuitLimit()));
        snapshot.setUpperCircuitLimit(parseDouble(data.getUpperCircuitLimit()));
        
        // Market data
        snapshot.setAverageTradePrice(parseDouble(data.getAverageTradePrice()));
        snapshot.setOpenInterest(parseDouble(data.getOpenInterest()));
        snapshot.setPrevOpenInterest(data.getPrevOpenInterest());
        snapshot.setMarketCapital(data.getMarketCapital());
        snapshot.setExposureCategory(data.getExposureCategory());
        
        // Time field
        snapshot.setLastTradeTime(data.getLastTradeTime());
        
        return snapshot;
    }

    private MarketSnapshot updateFromResponse(
            MarketSnapshot existing,
            MarketSnapshotResponse.MarketSnapshotData data) {
        
        // Update all fields from response
        existing.setLastTradedPrice(parseDouble(data.getLastTradedPrice()));
        existing.setNetChange(parseDouble(data.getNetChange()));
        existing.setPClose(parseDouble(data.getPClose()));
        existing.setOpen(parseDouble(data.getOpen()));
        existing.setHigh(parseDouble(data.getHigh()));
        existing.setLow(parseDouble(data.getLow()));
        existing.setClose(parseDouble(data.getPClose()));
        
        existing.setVolume(parseDouble(data.getVolume()));
        existing.setTotalBuyQuantity(parseDouble(data.getTotalBuyQuantity()));
        existing.setTotalSellQuantity(parseDouble(data.getTotalSellQuantity()));
        existing.setBuyQuantity(data.getBuyQuantity());
        existing.setSellQuantity(data.getSellQuantity());
        existing.setLastQuantity(data.getLastQuantity());
        
        existing.setAHigh(parseDouble(data.getAHigh()));
        existing.setALow(parseDouble(data.getALow()));
        existing.setLowerCircuitLimit(parseDouble(data.getLowerCircuitLimit()));
        existing.setUpperCircuitLimit(parseDouble(data.getUpperCircuitLimit()));
        
        existing.setAverageTradePrice(parseDouble(data.getAverageTradePrice()));
        existing.setOpenInterest(parseDouble(data.getOpenInterest()));
        existing.setPrevOpenInterest(data.getPrevOpenInterest());
        existing.setMarketCapital(data.getMarketCapital());
        existing.setExposureCategory(data.getExposureCategory());
        
        existing.setLastTradeTime(data.getLastTradeTime());
        
        return existing;
    }

    // Also update your old mapping to avoid confusion
    private void renameOldFieldsIfNeeded() {
        // If you had these old fields, you might want to rename them
        // lastRate -> lastTradedPrice
        // percentChange -> netChange  
        // totalQty -> volume
        // oi -> openInterest
        // etc.
    }
    
    private Double parseDouble(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Double parseDouble(Number value) {
        return value != null ? value.doubleValue() : null;
    }

    @PreDestroy
    public void cleanup() {
        executorService.shutdown();
    }
}