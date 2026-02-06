package com.analysis.jobs;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
public class VWAPJob {

    private final ScripCache scripCache;
    private final VWAPAPIExecutor vwapApiExecutor;
    private final AccessTokenService accessTokenService;

    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

    public VWAPJob(
            ScripCache scripCache,
            VWAPAPIExecutor vwapApiExecutor,
            AccessTokenService accessTokenService) {

        this.scripCache = scripCache;
        this.vwapApiExecutor = vwapApiExecutor;
        this.accessTokenService = accessTokenService;
    }

    public void run() {

        String accessToken = accessTokenService.getAccessToken();
        if (!StringUtils.hasText(accessToken)) {
            log.error("VWAP Job: Access token missing");
            return;
        }

        log.info("VWAP Job started");

        List<Map.Entry<Integer, ScripInfo>> scripList = new ArrayList<>();
        scripCache.getAllScripEntries().forEach(scripList::add);

        CountDownLatch latch = new CountDownLatch(scripList.size());

        for (Map.Entry<Integer, ScripInfo> entry : scripList) {

            executorService.submit(() -> {
                try {
                    processSingleScrip(
                            entry.getKey(),
                            entry.getValue().getSymbol(),
                            entry.getValue().getSector()
                    );
                } catch (Exception e) {
                    log.error(
                            "VWAP failed for {} ({})",
                            entry.getValue().getSymbol(),
                            entry.getKey(),
                            e
                    );
                } finally {
                    latch.countDown();
                }
            });
        }

        awaitCompletion(latch);
        log.info("VWAP Job completed");
    }

    private void processSingleScrip(
            int scripCode,
            String symbol,
            String sector) {

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

    private void awaitCompletion(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.MINUTES)) {
                log.warn("VWAP Job timeout. Pending: {}", latch.getCount());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("VWAP Job interrupted", e);
        }
    }

    @PreDestroy
    public void cleanup() {
        executorService.shutdown();
    }
}
