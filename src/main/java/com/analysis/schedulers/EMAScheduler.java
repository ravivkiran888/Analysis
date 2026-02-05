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
import org.springframework.scheduling.annotation.Scheduled;
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
public class EMAScheduler {

    private static final Logger log = LoggerFactory.getLogger(EMAScheduler.class);

    private final ScripCache scripCache;
    private final FivePaisaApiClient executor;
    private final EMAService emaService;
    private final AccessTokenService accessTokenService;

    // Fixed thread pool for ~250 scrips
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

    public EMAScheduler(
            ScripCache scripCache,
            FivePaisaApiClient executor,
            EMAService emaService,
            AccessTokenService accessTokenService) {

        this.scripCache = scripCache;
        this.executor = executor;
        this.emaService = emaService;
        this.accessTokenService = accessTokenService;
    }

    // Every 15 minutes between 9:00 AM and 3:30 PM (Mon–Fri IST)
    @Scheduled(cron = "0 */15 9-14 * * MON-FRI", zone = "Asia/Kolkata")
    @Scheduled(cron = "0 0-30/15 15 * * MON-FRI", zone = "Asia/Kolkata")
    public void run() {

        log.info("EMA Scheduler started");

        String accessToken = accessTokenService.getAccessToken();
        if (!StringUtils.hasText(accessToken)) {
            log.error("Access token not available. Skipping EMA job.");
            return;
        }

        String from = LocalDate.now().minusDays(10).toString();
        String to = LocalDate.now().minusDays(1).toString();

        List<Map.Entry<Integer, ScripInfo>> scripList = new ArrayList<>();
        scripCache.getAllScripEntries().forEach(scripList::add);

        int totalScrips = scripList.size();
        log.info("Processing {} scrips from {} to {}", totalScrips, from, to);

        CountDownLatch latch = new CountDownLatch(totalScrips);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failureCount = new AtomicInteger();

        for (Map.Entry<Integer, ScripInfo> entry : scripList) {
            executorService.submit(() -> {
                try {
                    processSingleScrip(entry.getKey(), entry.getValue(), from, to);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    log.error("Failed for {} ({}): {}",
                            entry.getValue().getSymbol(),
                            entry.getKey(),
                            e.getMessage());
                } finally {
                    latch.countDown();

                    long remaining = latch.getCount();
                    if (remaining % 25 == 0) {
                        log.info("EMA Progress: {}/{} completed",
                                totalScrips - remaining,
                                totalScrips);
                    }
                }
            });
        }

        try {
            boolean completed = latch.await(10, TimeUnit.MINUTES);

            if (!completed) {
                log.warn("EMA Scheduler timeout! {} tasks still pending", latch.getCount());
            }

            log.info("EMA Scheduler completed. Success: {}, Failed: {}",
                    successCount.get(),
                    failureCount.get());

        } catch (InterruptedException e) {
            log.error("EMA Scheduler interrupted", e);
            Thread.currentThread().interrupt();
        }
    }

    private void processSingleScrip(
            int scripCode,
            ScripInfo scripInfo,
            String from,
            String to) {

        String symbol = scripInfo.getSymbol();

        try {
            String json = executor.fetch30MinCandles(scripCode, from, to);
            emaService.processApiResponse(String.valueOf(scripCode), symbol, json);

        } catch (HttpClientErrorException.TooManyRequests e) {

            log.warn("Rate limit hit for {} ({}). Retrying once...", symbol, scripCode);

            try {
                Thread.sleep(2000);
                String json = executor.fetch30MinCandles(scripCode, from, to);
                emaService.processApiResponse(String.valueOf(scripCode), symbol, json);

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.error("Interrupted during retry for {} ({})", symbol, scripCode);

            } catch (Exception retryEx) {
                log.error("Retry failed for {} ({}): {}",
                        symbol, scripCode, retryEx.getMessage());
            }

        } catch (Exception e) {
            log.error("EMA processing failed for {} ({}): {}",
                    symbol, scripCode, e.getMessage());
        }
    }

    @PreDestroy
    public void cleanup() {
        executorService.shutdown();
    }
}
