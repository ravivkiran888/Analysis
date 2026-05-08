package com.analysis.scheduler;

import java.time.LocalTime;
import java.time.ZoneId;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.analysis.scanner.OptionsLevelScanner;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class OptionChainScheduler {

    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");

    // Market timings
    private static final LocalTime MARKET_START = LocalTime.of(9, 15);
    private static final LocalTime MARKET_END = LocalTime.of(15, 30);

    private final OptionsLevelScanner optionsLevelScanner;

    public OptionChainScheduler(OptionsLevelScanner optionsLevelScanner) {
        this.optionsLevelScanner = optionsLevelScanner;
    }

    /**
     * Runs every 5 minutes Monday-Friday
     */
    @Scheduled(cron = "0 */5 9-15 * * MON-FRI", zone = "Asia/Kolkata")
    public void executeOptionChainScan() {

        LocalTime now = LocalTime.now(IST_ZONE);

        log.info("⏰ Scheduler triggered at {}", now);

        // Skip before market open
        if (now.isBefore(MARKET_START)) {
            log.info("⏳ Market not opened yet");
            return;
        }

        // Skip after market close
        if (now.isAfter(MARKET_END)) {
            log.info("🛑 Market closed");
            return;
        }

        try {
            log.info("🚀 Starting Option Chain Scan");

            optionsLevelScanner.scan();

            log.info("✅ Option Chain Scan Completed");

        } catch (Exception e) {
            log.error("❌ Scheduler failed", e);
        }
    }
}