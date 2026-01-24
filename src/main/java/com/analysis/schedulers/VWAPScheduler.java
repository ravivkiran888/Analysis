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
    private final VWAPAPIExecutor vwapApiExecutor;

    public VWAPScheduler(
            ScripCache scripCache,
            VWAPAPIExecutor vwapApiExecutor) {

        this.scripCache = scripCache;
        this.vwapApiExecutor = vwapApiExecutor;
    }

    // To run every 30 minutes, Monday to Friday, between 9:30 AM and 3:30 PM IST, use this cron expression:
    
    @Scheduled(
    	    cron = "0 0,30 9-14 * * MON-FRI",
    	    zone = "Asia/Kolkata"
    	)
    public void runVWAPJob() {

        log.info(
            "VWAP Scheduler started at {}",
            LocalTime.now()
        );

        for (Map.Entry<Integer, String> entry :
                scripCache.getAllScripEntries()) {

            int scripCode = entry.getKey();
            String symbol = entry.getValue();
            	System.out.println(symbol);
            	
            try {
                List<VWAPRequest> requests =
                        VWAPApiBuilder.buildRequests(scripCode);

                vwapApiExecutor.execute(requests);

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
