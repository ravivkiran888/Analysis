package com.analysis.jobs;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.analysis.SignalState;
import com.analysis.documents.EMAValue;
import com.analysis.documents.VWAPValue;
import com.analysis.dto.ScripInfo;
import com.analysis.model.RSIValue;
import com.analysis.scanner.SignalStateDoc;
import com.analysis.services.ScripCache;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SignalScannerJob {

    private final MongoTemplate mongoTemplate;
    private final ScripCache scripCache;

    // Shared thread pool (DO NOT recreate every run)
    private final ExecutorService executorService = Executors.newFixedThreadPool(8);

    public SignalScannerJob(
            MongoTemplate mongoTemplate,
            ScripCache scripCache) {

        this.mongoTemplate = mongoTemplate;
        this.scripCache = scripCache;
    }

    public void run() {

        log.info("Signal Scanner Job started");

        var scripCodes = scripCache.getAllScripCodes();
        int total = scripCodes.size();

        CountDownLatch latch = new CountDownLatch(total);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failure = new AtomicInteger();

        for (Integer scripCode : scripCodes) {

            executorService.submit(() -> {
                try {
                    evaluateAndSave(scripCode);
                    success.incrementAndGet();
                } catch (Exception ex) {
                    failure.incrementAndGet();
                    log.error("Signal evaluation failed | scrip={}", scripCode, ex);
                } finally {
                    latch.countDown();
                }
            });
        }

        awaitCompletion(latch, success, failure);
    }

    /* ===================================================== */

    private void evaluateAndSave(Integer scripCode) {

        ScripInfo scripInfo = scripCache.getScripInfo(scripCode);
        if (scripInfo == null) {
            log.warn("Missing cache entry for scripCode {}", scripCode);
            return;
        }

        String code = String.valueOf(scripCode);
        String symbol = scripInfo.getSymbol();
        String sector = scripInfo.getSector();

        EMAValue ema = fetchEMA(code);
        VWAPValue vwap = fetchVWAP(code);
        RSIValue rsi = fetchRSI(code);

        // Dependency guard
        if (ema == null || vwap == null || rsi == null) {
            return;
        }

        SignalState newState =
                evaluateSignal(
                        ema.getEma20(),
                        ema.getEma50(),
                        vwap.getClose(),
                        vwap.getVwap(),
                        rsi.getRsi(),
                        vwap.getVwapSlope(),
                        vwap.getVolumeExpansion()
                );

        saveSignal(code, symbol, sector, newState);
    }

    /* ================= DECISION LOGIC ================= */

    private SignalState evaluateSignal(
            BigDecimal ema20,
            BigDecimal ema50,
            BigDecimal close,
            BigDecimal vwap,
            BigDecimal rsi,
            BigDecimal vwapSlope,
            BigDecimal volumeExpansion) {

      
    	// 1️⃣ Price above VWAP
    	if (close.compareTo(vwap) <= 0) {
    	    return SignalState.WAIT;
    	}

    	// 2️⃣ VWAP slope (slightly relaxed)
    	if (vwapSlope.compareTo(BigDecimal.valueOf(-0.02)) < 0) {
    	    return SignalState.WATCH;
    	}

    	// 3️⃣ RSI strength
    	if (rsi.compareTo(BigDecimal.valueOf(55)) <= 0) {
    	    return SignalState.WATCH;
    	}

    	// 4️⃣ Volume expansion
    	if (volumeExpansion.compareTo(BigDecimal.valueOf(1.5)) <= 0) {
    	    return SignalState.WATCH;
    	}

    	return SignalState.ENTRY_READY;

    	
    }


    /* ================= SINGLE WRITE ================= */

    private void saveSignal(
            String scripCode,
            String symbol,
            String sector,
            SignalState state) {

        Query query = Query.query(
                Criteria.where("ScripCode").is(scripCode)
        );

        Update update = new Update()
                .set("Symbol", symbol)
                .set("Sector", sector)
                .set("signalState", state)
                .set("evaluatedAt", Instant.now())
                .setOnInsert("ScripCode", scripCode);

        mongoTemplate.upsert(
                query,
                update,
                SignalStateDoc.class,
                "signal_states"
        );
    }

    /* ================= FETCH HELPERS ================= */

    private EMAValue fetchEMA(String scripCode) {
        return mongoTemplate.findOne(
                Query.query(Criteria.where("ScripCode").is(scripCode)),
                EMAValue.class,
                "ema_5m"
        );
    }

    private VWAPValue fetchVWAP(String scripCode) {
        return mongoTemplate.findOne(
                Query.query(Criteria.where("ScripCode").is(scripCode)),
                VWAPValue.class,
                "vwap_values"
        );
    }

    private RSIValue fetchRSI(String scripCode) {
        return mongoTemplate.findOne(
                Query.query(Criteria.where("ScripCode").is(scripCode)),
                RSIValue.class,
                "rsi_values"
        );
    }

    private void awaitCompletion(
            CountDownLatch latch,
            AtomicInteger success,
            AtomicInteger failure) {

        try {
            boolean completed = latch.await(3, TimeUnit.MINUTES);

            if (!completed) {
                log.warn("Signal scan timeout! Pending={}", latch.getCount());
            }

            log.info(
                    "Signal scan completed | success={} failed={}",
                    success.get(),
                    failure.get()
            );

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Signal scan interrupted", e);
        }
    }

    @PreDestroy
    public void cleanup() {
        executorService.shutdown();
    }
}
