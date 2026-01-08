package com.analysis.schedulers;

import java.time.LocalTime;
import java.time.ZoneId;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.analysis.APPConstants;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

@Component
public class MarketScheduler {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final LocalTime MARKET_END = LocalTime.of(15, 25);

     // Triggers once per trading day at 09:20 AM IST (Monday–Friday) to start market-hour processing

    @Scheduled(cron = "0 20 9 * * MON-FRI", zone = "Asia/Kolkata")
    @SchedulerLock(
        name = APPConstants.VWAP_SCHEDULER,
        lockAtMostFor = APPConstants.LOCK_FOR_IN_MINS
    )
    public void startAtNineTwenty() {
        runIfWithinMarket();
    }

 // Executes every 30 minutes at :20 and :50 between 09:00 AM and 03:00 PM IST, Monday–Friday

    @Scheduled(cron = "0 50,20 9-15 * * MON-FRI", zone = "Asia/Kolkata")
  
    @SchedulerLock(
        name = APPConstants.VWAP_SCHEDULER,
        lockAtMostFor = APPConstants.LOCK_FOR_IN_MINS
    )
    public void everyThirtyMinutes() {
        runIfWithinMarket();
    }

    private void runIfWithinMarket() {
        LocalTime now = LocalTime.now(IST);
        if (now.isAfter(MARKET_END)) {
            return;
        }
        runMarketJob();
    }

    private void runMarketJob() {
    	
    	System.out.println("VWAP  Data");
    	
        // Rate-limited API calls
    }
}
