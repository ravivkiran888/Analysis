package com.analysis.runner;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.analysis.helpers.VWAPApiBuilder;
import com.analysis.requests.VWAPRequest;
import com.analysis.services.ScripCache;
import com.analysis.util.VWAPAPIExecutor;

@Component
public class VWAPRunner {
	
	 @Value("${runvwap:false}")
	  private boolean runVwap;

    private static final Logger log =
            LoggerFactory.getLogger(VWAPRunner.class);

    private final VWAPAPIExecutor executor;
    private final ScripCache scripCache;

    public VWAPRunner(
    		VWAPAPIExecutor executor,
            ScripCache scripCache) {

        this.executor = executor;
        this.scripCache = scripCache;
    }
  
    @Scheduled(cron = "0 30 9 * * MON-FRI", zone = "Asia/Kolkata")
    @Scheduled(cron = "0 */30 10-15 * * MON-FRI", zone = "Asia/Kolkata")
    
    public void run(String... args) {
    	
    	if (runVwap) {
    		
    	    long start = System.nanoTime();

    		
        log.info("VWAP Runner started");
        	System.out.println("VWAP Runner started");
        for (Map.Entry<Integer, String> entry :
                scripCache.getAllScripEntries()) {

            int scripCode = entry.getKey();
            String symbol = entry.getValue();

//            System.out.println(symbol);

            try {
                List<VWAPRequest> requests =
                        VWAPApiBuilder.buildRequests(scripCode);

                executor.execute(requests);

            } catch (Exception ex) {
                log.error(
                    "VWAP failed for {} ({})",
                    symbol,
                    scripCode,
                    ex
                );
            }
        }
        
        System.out.println("VWAP Runner completed");
        log.info("VWAP Runner completed");
        
        long end = System.nanoTime();

        
        long durationMs = (end - start) / 1_000_000;

        System.out.println("Duration took "+durationMs);
        
    	}
    	
    	else
    	{
    		System.out.println("###########################");
    		System.out.println("VWAP Disabled");
    	}
    }
}
