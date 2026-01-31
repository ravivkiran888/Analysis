package com.analysis.services.impl;


import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.analysis.SignalState;
import com.analysis.documents.EMAValue;
import com.analysis.documents.VWAPValue;
import com.analysis.model.RSIValue;
import com.analysis.scanner.SignalStateDoc;
import com.analysis.services.ScripCache;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SignalScannerService {

    private final MongoTemplate mongoTemplate;
    private final ScripCache scripCache;

    public SignalScannerService(
            MongoTemplate mongoTemplate,
            ScripCache scripCache) {

        this.mongoTemplate = mongoTemplate;
        this.scripCache = scripCache;
    }


 @Scheduled(cron = "0 */10 * * * *", zone = "Asia/Kolkata")
public void scanAll() {

        log.info("Signal scan started");

        for (Integer scripCode : scripCache.getAllScripCodes()) {
            try {
                evaluateAndSave(scripCode);
            } catch (Exception ex) {
                log.error(
                    "Signal evaluation failed | scrip={}",
                    scripCode,
                    ex
                );
            }
        }

        log.info("Signal scan completed");
    }

    /* ===================================================== */

    private void evaluateAndSave(Integer scripCode) {

        String code = String.valueOf(scripCode);
        String symbol = scripCache.getSymbol(scripCode);

        EMAValue ema = fetchEMA(code);
        VWAPValue vwap = fetchVWAP(code);
        RSIValue rsi = fetchRSI(code);

        // If any dependency missing → skip
        if (ema == null || vwap == null || rsi == null) {
            return;
        }

        SignalState newState =
                evaluateSignal(
                        ema.getEma20(),
                        ema.getEma50(),
                        vwap.getClose(),
                        vwap.getVwap(),
                        rsi.getRsi()
                );

        saveSignal(code, symbol, newState);
    }

    /**
     * PURE DECISION LOGIC
     */
    private SignalState evaluateSignal(
            BigDecimal ema20,
            BigDecimal ema50,
            BigDecimal close,
            BigDecimal vwap,
            BigDecimal rsi) {

        // 1️⃣ Trend (30m)
        if (ema20.compareTo(ema50) <= 0) {
            return SignalState.WAIT;
        }

        // 2️⃣ VWAP confirmation (5m)
        if (close.compareTo(vwap) <= 0) {
            return SignalState.WATCH;
        }

        // 3️⃣ Momentum confirmation
        if (rsi.compareTo(BigDecimal.valueOf(40)) <= 0) {
            return SignalState.WATCH;
        }

        return SignalState.ENTRY_READY;
    }

    /**
     * SINGLE WRITE POINT
     */
    
    private void saveSignal(
            String scripCode,
            String symbol,
            SignalState state) {

        Query query = Query.query(
                Criteria.where("ScripCode").is(scripCode)
        );

        Update update = new Update()
                .set("Symbol", symbol)
                .set("signalState", state)
                .set("evaluatedAt", Instant.now())
                .setOnInsert("ScripCode", scripCode);

        mongoTemplate.upsert(
                query,
                update,
                SignalStateDoc.class,
                "signal_states"
        );

        log.info(
            "Signal updated | scrip={} | state={}",
            scripCode,
            state
        );
    }


    /* ================= FETCH HELPERS ================= */

    private EMAValue fetchEMA(String scripCode) {
        return mongoTemplate.findOne(
                Query.query(
                    Criteria.where("ScripCode").is(scripCode)
                ),
                EMAValue.class,
                "ema_30m"
        );
    }

    private VWAPValue fetchVWAP(String scripCode) {
        return mongoTemplate.findOne(
                Query.query(
                    Criteria.where("ScripCode").is(scripCode)
                ),
                VWAPValue.class,
                "vwap_values"
        );
    }

    private RSIValue fetchRSI(String scripCode) {
        return mongoTemplate.findOne(
                Query.query(
                    Criteria.where("ScripCode").is(scripCode)
                ),
                RSIValue.class,
                "rsi_values"
        );
    }
}
