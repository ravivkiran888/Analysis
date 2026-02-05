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

import com.analysis.dto.ScripInfo;
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

        // ---- Load cached scrips (scripCode -> ScripInfo) ----
        List<Map.Entry<Integer, ScripInfo>> scripList = new ArrayList<>();
        scripCache.getAllScripEntries().forEach(scripList::add);

        int totalScrips = scripList.size();
        CountDownLatch latch = new CountDownLatch(totalScrips);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // ---- Submit tasks ----
        for (Map.Entry<Integer, ScripInfo> entry : scripList) {

            executorService.submit(() -> {
                try {
                    int scripCode = entry.getKey();
                    ScripInfo info = entry.getValue();

                    processSingleScrip(
                            scripCode,
                            info.getSymbol(),
                            info.getSector()
                    );

                    successCount.incrementAndGet();

                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    log.error(
                            "VWAP failed for {} ({})",
                            entry.getValue().getSymbol(),
                            entry.getKey(),
                            e
                    );
                } finally {
                    latch.countDown();

                    long remaining = latch.getCount();
                    if (remaining % 25 == 0) {
                        log.info(
                                "VWAP Progress: {}/{} completed",
                                totalScrips - remaining,
                                totalScrips
                        );
                    }
                }
            });
        }

        // ---- Await completion ----
        try {
            boolean completed = latch.await(5, TimeUnit.MINUTES);

            if (completed) {
                log.info(
                        "VWAP Scheduler completed. Success: {}, Failed: {}",
                        successCount.get(),
                        failureCount.get()
                );
            } else {
                log.warn(
                        "VWAP Scheduler timeout! {} tasks still pending",
                        latch.getCount()
                );
            }

        } catch (InterruptedException e) {
            log.error("VWAP Scheduler interrupted", e);
            Thread.currentThread().interrupt();
        }

        log.info("VWAP Scheduler ended at {}", LocalTime.now());
    }

    private void processSingleScrip(
            int scripCode,
            String symbol,
            String sector
    ) {
        try {
            log.debug("Processing VWAP for {} [{}]", symbol, sector);

            List<VWAPRequest> requests =
                    VWAPApiBuilder.buildRequests(scripCode);

            vwapApiExecutor.execute(requests);

        } catch (Exception ex) {
            log.error("VWAP failed for {} ({})", symbol, scripCode, ex);
            throw new RuntimeException(ex);
        }
    }

    @PreDestroy
    public void cleanup() {
        executorService.shutdown();
    }
}
