package com.analysis.orchestrator;


import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.analysis.jobs.MarketSnapshotJob;
import com.analysis.jobs.SignalScannerJob;
import com.analysis.jobs.VWAPJob;
import com.analysis.schedulers.EMAJob;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class MarketScanOrchestrator {

    private final VWAPJob vwapJob;
    private final EMAJob emaJob;
    private final SignalScannerJob signalScannerJob;
    private final MarketSnapshotJob marketSnapshotJob;

    public MarketScanOrchestrator(VWAPJob vwapJob, EMAJob emaJob,SignalScannerJob signalScannerJob,  MarketSnapshotJob marketSnapshotJob) {
        this.vwapJob = vwapJob;
        this.emaJob = emaJob;
        this.signalScannerJob = signalScannerJob;
        this.marketSnapshotJob = marketSnapshotJob;
    }

    @Scheduled(cron = "0 */13 9-15 * * MON-FRI", zone = "Asia/Kolkata")
    public void orchestrate() {

        log.info("Market Scan Orchestrator started");

        long start = System.currentTimeMillis();

        boolean vwapOk = runJob("VWAP", vwapJob::run);
        boolean emaOk  = runJob("EMA",  emaJob::run);
        boolean marketSnapOk  = runJob("MARKET_SNAP", marketSnapshotJob::run);
       boolean sigOk  = runJob("SIGNAL", signalScannerJob::run);
        
        
        log.info(
            "Market Scan completed in {} ms | VWAP={} EMA={} SIGNAL={} MARKET_SNAP={}",
            System.currentTimeMillis() - start,
            status(vwapOk),
            status(emaOk),
            status(sigOk),
            status(marketSnapOk)
        );
        
    }

    private boolean runJob(String name, Runnable job) {

        long start = System.currentTimeMillis();

        try {
            log.info("{} Job started", name);
            job.run();
            log.info("{} Job completed in {} ms",
                    name, System.currentTimeMillis() - start);
            return true;

        } catch (Exception e) {
            log.error("{} Job FAILED after {} ms",
                    name, System.currentTimeMillis() - start, e);
            return false;
        }
    }

    private String status(boolean ok) {
        return ok ? "OK" : "FAILED";
    }
}