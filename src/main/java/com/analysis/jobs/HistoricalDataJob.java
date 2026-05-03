package com.analysis.jobs;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import com.analysis.documents.ScripMaster;
import com.analysis.documents.SymbolIndicators;
import com.analysis.repository.ScripMasterRepository;
import com.analysis.service.AccessTokenService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class HistoricalDataJob {

    private static final int BATCH_SIZE = 3;
    private static final int THREAD_POOL_SIZE = 5;
    private static final int BATCH_DELAY_MS = 1000;

    private final ScripMasterRepository scripMasterRepository;
    private final MongoTemplate mongoTemplate;
    private final AccessTokenService accessTokenService;
    private final RestTemplate restTemplate;

    private final ExecutorService executorService =
            Executors.newFixedThreadPool(THREAD_POOL_SIZE);

    // ✅ Resolved once
    private String tradingDate;

    public HistoricalDataJob(
            ScripMasterRepository scripMasterRepository,
            MongoTemplate mongoTemplate,
            AccessTokenService accessTokenService,
            RestTemplate restTemplate) {

        this.scripMasterRepository = scripMasterRepository;
        this.mongoTemplate = mongoTemplate;
        this.accessTokenService = accessTokenService;
        this.restTemplate = restTemplate;
    }

    // ================= RUN =================

    @Scheduled(cron = "0 32 9 ? * MON-FRI", zone = "Asia/Kolkata")
    public void run() {

        log.info("📊 Historical Candle Job Started");

        String accessToken = accessTokenService.getAccessToken();

        if (!StringUtils.hasText(accessToken)) {
            log.error("❌ Missing Access Token");
            return;
        }

        // 🔥 Resolve date once
        this.tradingDate = resolveTradingDate(accessToken);
        log.info("📅 Using trading date: {}", tradingDate);

        List<ScripMaster> allScripts = scripMasterRepository.findAll();

        if (allScripts.isEmpty()) {
            log.warn("No scrips found");
            return;
        }

        List<List<ScripMaster>> batches = createBatches(allScripts);

        CountDownLatch latch = new CountDownLatch(batches.size());
        AtomicInteger successCount = new AtomicInteger();

        for (int i = 0; i < batches.size(); i++) {

            final int batchIndex = i;
            List<ScripMaster> batch = batches.get(i);

            executorService.submit(() -> {
                try {

                    if (batchIndex > 0) {
                        Thread.sleep(BATCH_DELAY_MS);
                    }

                    boolean success = processBatch(batch, accessToken);

                    if (success) {
                        successCount.addAndGet(batch.size());
                    } else {
                        retryBatchIndividually(batch, accessToken);
                    }

                } catch (Exception e) {
                    log.error("Batch {} failed", batchIndex, e);
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            latch.await(10, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        log.info("✅ Completed. Success: {}", successCount.get());
    }

    // ================= BATCH =================

    private boolean processBatch(List<ScripMaster> batch, String token) {

        int success = 0;

        for (ScripMaster scrip : batch) {
            if (processSingleScrip(scrip, token)) {
                success++;
            }
        }

        log.info("Batch processed: {}/{}", success, batch.size());
        return success > 0;
    }

    // ================= SINGLE =================

    private boolean processSingleScrip(ScripMaster scrip, String token) {

        try {
            String url = buildUrl(scrip.getScripCode());

            var headers = new org.springframework.http.HttpHeaders();
            headers.set("Authorization", "Bearer " + token);
            headers.set("x-clientcode", "50407824");

            var entity = new org.springframework.http.HttpEntity<>(headers);

            var response = restTemplate.exchange(
                    url,
                    org.springframework.http.HttpMethod.GET,
                    entity,
                    java.util.Map.class
            );

            if (response.getBody() == null) return false;

            List candles = extractCandles(response.getBody());

            if (candles == null || candles.isEmpty()) {
                log.warn("No candle for {}", scrip.getSymbol());
                return false;
            }

            List candle = (List) candles.get(0);

            return enrichSymbolIndicators(scrip, candle);

        } catch (Exception e) {
            log.error("Failed for {}", scrip.getSymbol(), e);
            return false;
        }
    }

    // ================= ENRICH =================

    private boolean enrichSymbolIndicators(ScripMaster scrip, List candle) {

        try {
            String scripCode = scrip.getScripCode();

            BigDecimal open  = toDecimal(candle.get(1));
            BigDecimal high  = toDecimal(candle.get(2));
            BigDecimal low   = toDecimal(candle.get(3));
            BigDecimal close = toDecimal(candle.get(4));

            Query query = new Query(Criteria.where("scripCode").is(scripCode));

            Update update = new Update()
                    .set("prevOpen", open)
                    .set("prevHigh", high)
                    .set("prevLow", low)
                    .set("prevClose", close)
                    .set("timestamp", Instant.now());

            mongoTemplate.upsert(query, update, SymbolIndicators.class);

            return true;

        } catch (Exception e) {
            log.error("Enrich failed {}", scrip.getSymbol(), e);
            return false;
        }
    }

    // ================= URL =================

    private String buildUrl(String scripCode) {
        return "https://openapi.5paisa.com/V2/historical/N/C/"
                + scripCode
                + "/1d?from=" + tradingDate + "&end=" + tradingDate;
    }

    // ================= DATE RESOLUTION =================

    private String resolveTradingDate(String token) {

        String testScrip = "1660"; // ITC
        LocalDate date = LocalDate.now(ZoneId.of("Asia/Kolkata")).minusDays(1);

        while (true) {

            if (date.getDayOfWeek() == DayOfWeek.SATURDAY ||
                date.getDayOfWeek() == DayOfWeek.SUNDAY) {

                date = date.minusDays(1);
                continue;
            }

            if (hasData(date, testScrip, token)) {
                return date.toString();
            }

            date = date.minusDays(1);
        }
    }

    private boolean hasData(LocalDate date, String scripCode, String token) {

        try {
            String url = "https://openapi.5paisa.com/V2/historical/N/C/"
                    + scripCode
                    + "/1d?from=" + date + "&end=" + date;

            var headers = new org.springframework.http.HttpHeaders();
            headers.set("Authorization", "Bearer " + token);
            headers.set("x-clientcode", "50407824");

            var entity = new org.springframework.http.HttpEntity<>(headers);

            var response = restTemplate.exchange(
                    url,
                    org.springframework.http.HttpMethod.GET,
                    entity,
                    java.util.Map.class
            );

            if (response.getBody() == null) return false;

            List candles = extractCandles(response.getBody());

            return candles != null && !candles.isEmpty();

        } catch (Exception e) {
            return false;
        }
    }

    // ================= HELPERS =================

    private List extractCandles(Object body) {
        try {
            var map = (java.util.Map) body;
            var data = (java.util.Map) map.get("data");
            return (List) data.get("candles");
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal toDecimal(Object val) {
        if (val == null) return null;
        return new BigDecimal(val.toString());
    }

    private List<List<ScripMaster>> createBatches(List<ScripMaster> list) {

        List<List<ScripMaster>> batches = new ArrayList<>();

        for (int i = 0; i < list.size(); i += BATCH_SIZE) {
            batches.add(list.subList(i, Math.min(i + BATCH_SIZE, list.size())));
        }

        return batches;
    }

    private void retryBatchIndividually(List<ScripMaster> batch, String token) {

        log.warn("Retrying batch individually");

        for (ScripMaster scrip : batch) {
            try {
                Thread.sleep(200);
                processSingleScrip(scrip, token);
            } catch (Exception ignored) {}
        }
    }

    @jakarta.annotation.PreDestroy
    public void shutdown() {
        executorService.shutdown();
    }
}