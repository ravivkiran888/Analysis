package com.analysis.schedulers;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;

import com.analysis.dto.ScripInfo;
import com.analysis.helpers.FivePaisaApiClient;
import com.analysis.services.AccessTokenService;
import com.analysis.services.EMAService;
import com.analysis.services.ScripCache;

import jakarta.annotation.PreDestroy;

@Service
public class EMAJob {

    private static final Logger log = LoggerFactory.getLogger(EMAJob.class);

    private final ScripCache scripCache;
    private final FivePaisaApiClient apiClient;
    private final EMAService emaService;
    private final AccessTokenService accessTokenService;

    // Increased thread pool for faster processing of 5m data
    private final ExecutorService executorService = Executors.newFixedThreadPool(15);

    public EMAJob(
            ScripCache scripCache,
            FivePaisaApiClient apiClient,
            EMAService emaService,
            AccessTokenService accessTokenService) {

        this.scripCache = scripCache;
        this.apiClient = apiClient;
        this.emaService = emaService;
        this.accessTokenService = accessTokenService;
    }

    public void run() {

        log.info("5m EMA Job started");

        String accessToken = accessTokenService.getAccessToken();
        if (!StringUtils.hasText(accessToken)) {
            log.error("5m EMA Job: Access token missing. Skipping.");
            return;
        }

        // For 5m candles, we need fewer days of history
        // 3 days gives us ~864 candles (3 days * 288 candles/day) which is enough
        String from = LocalDate.now().minusDays(3).toString();
        String to = LocalDate.now().toString();  // Include today for latest data

        List<Map.Entry<Integer, ScripInfo>> scripList = new ArrayList<>();
        scripCache.getAllScripEntries().forEach(scripList::add);

        int totalScrips = scripList.size();
        log.info("Processing {} scrips with 5m candles from {} to {}", totalScrips, from, to);

        CountDownLatch latch = new CountDownLatch(totalScrips);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failureCount = new AtomicInteger();

        for (Map.Entry<Integer, ScripInfo> entry : scripList) {

            executorService.submit(() -> {
                try {
                    processSingleScrip(
                            entry.getKey(),
                            entry.getValue(),
                            from,
                            to
                    );
                    successCount.incrementAndGet();

                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    log.error(
                            "5m EMA failed for {} ({})",
                            entry.getValue().getSymbol(),
                            entry.getKey(),
                            e
                    );
                } finally {
                    latch.countDown();

                    long remaining = latch.getCount();
                    if (remaining > 0 && remaining % 25 == 0) {
                        log.info(
                                "5m EMA Progress: {}/{} completed ({}%)",
                                totalScrips - remaining,
                                totalScrips,
                                String.format("%.1f", (totalScrips - remaining) * 100.0 / totalScrips)
                        );
                    }
                }
            });
        }

        awaitCompletion(latch, successCount, failureCount);
    }

    private void processSingleScrip(
            int scripCode,
            ScripInfo scripInfo,
            String from,
            String to) {

        String symbol = scripInfo.getSymbol();

        try {
            // Use 5m candles instead of 30m
            String json = apiClient.fetch5MinCandles(scripCode, from, to);
            emaService.processApiResponse(String.valueOf(scripCode), symbol, json);

        } catch (HttpClientErrorException.TooManyRequests e) {

            log.warn("Rate limit hit for {} ({}). Retrying once...", symbol, scripCode);

            try {
                Thread.sleep(3000);  // Increased delay for rate limiting

                String json = apiClient.fetch5MinCandles(scripCode, from, to);
                emaService.processApiResponse(String.valueOf(scripCode), symbol, json);

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.error("Interrupted during retry for {} ({})", symbol, scripCode);

            } catch (Exception retryEx) {
                log.error(
                        "Retry failed for {} ({}): {}",
                        symbol,
                        scripCode,
                        retryEx.getMessage()
                );
            }

        } catch (Exception e) {
            log.error(
                    "5m EMA processing failed for {} ({}): {}",
                    symbol,
                    scripCode,
                    e.getMessage()
            );
        }
    }

    private void awaitCompletion(
            CountDownLatch latch,
            AtomicInteger successCount,
            AtomicInteger failureCount) {

        try {
            // Increased timeout for 5m data (more candles to process)
            boolean completed = latch.await(15, TimeUnit.MINUTES);

            if (!completed) {
                log.warn("5m EMA Job timeout! {} tasks still pending", latch.getCount());
            }

            log.info(
                    "5m EMA Job completed. Success: {}, Failed: {}",
                    successCount.get(),
                    failureCount.get()
            );

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("5m EMA Job interrupted", e);
        }
    }

    @PreDestroy
    public void cleanup() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}