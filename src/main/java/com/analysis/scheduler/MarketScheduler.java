package com.analysis.scheduler;

import java.time.LocalTime;
import java.time.ZoneId;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.analysis.jobs.MarketSnapshotJob;
import com.analysis.scanner.SymbolScanner;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class MarketScheduler {

    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");
    
    private final SymbolScanner symbolScanner;
    private final MarketSnapshotJob marketSnapshotJob;

    public MarketScheduler(SymbolScanner symbolScanner, MarketSnapshotJob marketSnapshotJob) {
        this.symbolScanner = symbolScanner;
        this.marketSnapshotJob = marketSnapshotJob;
        log.info("MarketScheduler initialized");
    }


    @Scheduled(cron = "0 */5 9-15 * * MON-FRI", zone = "Asia/Kolkata")
    @Scheduled(cron = "0 30 15 * * MON-FRI", zone = "Asia/Kolkata")
    public void executeMarketScan() {
        
        LocalTime startTime = LocalTime.now(IST_ZONE);
        log.info("🚀 Starting market scan at {}", startTime);

        long start = System.currentTimeMillis();

        try {
            // Run SymbolScanner
            log.info("Running SymbolScanner...");
            symbolScanner.scan();
            
            // Small delay between jobs
            Thread.sleep(2000);
            
            // Run MarketSnapshotJob
            log.info("Running MarketSnapshotJob...");
            marketSnapshotJob.run();
            
            long duration = System.currentTimeMillis() - start;
            log.info("✅ Market scan completed in {} ms at {}", duration, LocalTime.now(IST_ZONE));
            
        } catch (Exception e) {
            log.error("❌ Market scan failed: {}", e.getMessage());
        }
    }
}