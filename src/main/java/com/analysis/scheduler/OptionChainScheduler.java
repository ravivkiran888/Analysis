package com.analysis.scheduler;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Set;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.analysis.scanner.OptionsLevelScanner;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class OptionChainScheduler {

    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");

    private static final LocalTime MARKET_OPEN = LocalTime.of(9, 15);

    private static final LocalTime MARKET_CLOSE = LocalTime.of(15, 30);

    private final OptionsLevelScanner optionsLevelScanner;

    /*
     * NSE Holidays
     * Add dynamically later from DB/config if needed
     */
    private static final Set<LocalDate> NSE_HOLIDAYS = Set.of(
            // Example:
            // LocalDate.of(2026, 1, 26),
            // LocalDate.of(2026, 3, 14)
    );

    public OptionChainScheduler(
            OptionsLevelScanner optionsLevelScanner) {

        this.optionsLevelScanner = optionsLevelScanner;
    }

    /**
     * Runs every 5 minutes.
     * Market-hour validation handled inside code.
     */
    @Scheduled(fixedDelay = 300000)
    public void executeOptionChainScan() {

        LocalDate today = LocalDate.now(IST_ZONE);

        LocalTime now = LocalTime.now(IST_ZONE);

        DayOfWeek day = today.getDayOfWeek();

        log.info("⏰ Scheduler triggered at {}", now);

        // =========================
        // Skip Weekends
        // =========================
        if (day == DayOfWeek.SATURDAY
                || day == DayOfWeek.SUNDAY) {

            log.info("📴 Market closed - Weekend");

            return;
        }

        // =========================
        // Skip NSE Holidays
        // =========================
        if (NSE_HOLIDAYS.contains(today)) {

            log.info("📴 Market closed - NSE Holiday");

            return;
        }

        // =========================
        // Skip Non-Market Hours
        // =========================
        if (now.isBefore(MARKET_OPEN)
                || now.isAfter(MARKET_CLOSE)) {

            log.info(
                    "📴 Outside market hours | Current Time={}",
                    now);

            return;
        }

        try {

            log.info("🚀 Starting Option Chain Scan");

            optionsLevelScanner.scan();

            log.info("✅ Option Chain Scan Completed");

        } catch (Exception e) {

            log.error(
                    "❌ Scheduler execution failed",
                    e);
        }
    }
}