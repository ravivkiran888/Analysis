package com.analysis.schedulers;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.analysis.helpers.VWAPApiBuilder;
import com.analysis.requests.VWAPRequest;
import com.analysis.services.ScripCache;
import com.analysis.util.VWAPAPIExecutor;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class VWAPScheduler {

    private final ScripCache scripCache;
    private final VWAPAPIExecutor vwapaAPIExecutor;

    public VWAPScheduler(
            ScripCache scripCache,
            VWAPAPIExecutor vwapaAPIExecutor
    ) {
        this.scripCache = scripCache;
        this.vwapaAPIExecutor = vwapaAPIExecutor;
    }

    /**
     * Runs every 5 minutes during market hours
     * 9:15 AM – 3:30 PM (MON–FRI)
     */
    @Scheduled(
        cron = "0 */5 9-15 * * MON-FRI",
        zone = "Asia/Kolkata"
    )
    public void runVWAPJob() {

        LocalTime now = LocalTime.now();
        log.info("VWAP Scheduler started at {}", now);

        for (Map.Entry<Integer, String> entry :
                scripCache.getAllScripEntries()) {

            int scripCode = entry.getKey();
            String symbol = entry.getValue();
            
            System.out.println(symbol);

            try {
                log.debug("Processing VWAP | {} ({})", symbol, scripCode);

                List<VWAPRequest> requests =
                        VWAPApiBuilder.buildRequests(scripCode);

                vwapaAPIExecutor.executeInBatches(requests, 50);

                // API rate-limit safety
                Thread.sleep(60);

            } catch (Exception ex) {
                log.error(
                    "VWAP failed for {} ({})",
                    symbol,
                    scripCode,
                    ex
                );
            }
        }

        log.info("VWAP Scheduler completed");
    }
}
