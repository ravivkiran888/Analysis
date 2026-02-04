package com.analysis.schedulers;


import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.analysis.helpers.VWAPApiBuilder;
import com.analysis.requests.VWAPRequest;
import com.analysis.services.AccessTokenService;
import com.analysis.services.ScripCache;
import com.analysis.util.VWAPAPIExecutor;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class VWAPScheduler {

    private final ScripCache scripCache;
    private final VWAPAPIExecutor vwapApiExecutor;
    private final AccessTokenService accessTokenService;
    
    // Fixed thread pool for parallel execution
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

    public VWAPScheduler(
            ScripCache scripCache,
            VWAPAPIExecutor vwapApiExecutor,
            AccessTokenService accessTokenService) {

        this.scripCache = scripCache;
        this.vwapApiExecutor = vwapApiExecutor;
        this.accessTokenService = accessTokenService;
    }

    @Scheduled(cron = "0 */10 9-15 * * *", zone = "Asia/Kolkata")
    public void runVWAPJob() {
        
        String accessToken = accessTokenService.getAccessToken();

        if (!StringUtils.hasText(accessToken)) {
            log.error("Access token not available. Skipping VWAP job.");
            return;
        }

        log.info("VWAP Scheduler started at {}", LocalTime.now());

        // Convert to list for easier processing
        List<Map.Entry<Integer, String>> scripList = new ArrayList<>();
        scripCache.getAllScripEntries().forEach(scripList::add);
        
        int totalScrips = scripList.size();
        CountDownLatch latch = new CountDownLatch(totalScrips);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // Submit all tasks
        for (Map.Entry<Integer, String> entry : scripList) {
            executorService.submit(() -> {
                try {
                    processSingleScrip(entry.getKey(), entry.getValue());
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    log.error("Failed for {} ({}): {}", entry.getValue(), entry.getKey(), e.getMessage());
                } finally {
                    latch.countDown();
                    
                    // Log progress
                    long remaining = latch.getCount();
                    if (remaining % 25 == 0) { // Log every 25 scrips
                        log.info("VWAP Progress: {}/{} completed", 
                                totalScrips - remaining, totalScrips);
                    }
                }
            });
        }

        try {
            // Wait for completion (max 5 minutes for 250 scrips)
            boolean completed = latch.await(5, TimeUnit.MINUTES);
            
            if (completed) {
                log.info("VWAP Scheduler completed. Success: {}, Failed: {}", 
                        successCount.get(), failureCount.get());
            } else {
                log.warn("VWAP Scheduler timeout! {} tasks still pending", latch.getCount());
            }
            
        } catch (InterruptedException e) {
            log.error("VWAP Scheduler interrupted", e);
            Thread.currentThread().interrupt();
        }
        
        
        log.info("VWAP Scheduler Ended  at {}", LocalTime.now());
    }

    private void processSingleScrip(int scripCode, String symbol) {
        try {
            // Build request for this scrip
            List<VWAPRequest> requests = VWAPApiBuilder.buildRequests(scripCode);
            
            // Execute (rate limiter inside will handle timing)
            vwapApiExecutor.execute(requests);
            
        } catch (Exception ex) {
            log.error("VWAP failed for {} ({})", symbol, scripCode, ex);
            throw new RuntimeException(ex); // Let the submit() wrapper handle it
        }
    }

    @PreDestroy
    public void cleanup() {
        executorService.shutdown();
    }
}