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
    
    private final OptionsLevelScanner optionsLevelScanner;
   
    public OptionChainScheduler(OptionsLevelScanner optionsLevelScanner) {
       this.optionsLevelScanner = optionsLevelScanner;
    }


    @Scheduled(cron = "0 */5 9-15 * * MON-FRI", zone = "Asia/Kolkata")
    @Scheduled(cron = "0 30 15 * * MON-FRI", zone = "Asia/Kolkata")
    public void executeOptionChainScan() {
        
        LocalTime startTime = LocalTime.now(IST_ZONE);
        log.info("🚀 Starting Option Chain scan at {}", startTime);

     
        try {
            // Run optionsLevelScanner
            log.info("run optionsLevelScanner...");
            optionsLevelScanner.scan();
            
            // Small delay between jobs
            Thread.sleep(2000);
            
          
            
        } catch (Exception e) {
            log.error("❌ Market scan failed: {}", e.getMessage());
        }
    }
}