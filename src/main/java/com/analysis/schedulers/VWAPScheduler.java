package com.analysis.schedulers;

import java.util.ArrayList;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.analysis.helpers.VWAPApiBuilder;
import com.analysis.requests.VWAPRequest;
import com.analysis.services.ScripCache;
import com.analysis.services.VWAPService;
import com.analysis.util.VWAPAPIExecutor;

@Component
public class VWAPScheduler {

    private final ScripCache scripCache;
    private final VWAPService vwapService;
    private final VWAPAPIExecutor	 vwapaAPIExecutor;
    
    

    public VWAPScheduler(
            ScripCache scripCache,
            VWAPService vwapService,
            VWAPAPIExecutor vwapaAPIExecutor) {
        this.scripCache = scripCache;
        this.vwapService = vwapService;
        this.vwapaAPIExecutor = vwapaAPIExecutor;
     }
    
    @Scheduled(cron = "0 */5 * * * *")
    public void startVWAPJob() {
        runVWAPJob();
    }

    public void runVWAPJob() {

    	
        /*
        List<Integer> scripCodes =
                new ArrayList<>(scripCache.getAllScripCodes());
        */

        List<Integer> scripCodes = new ArrayList<>();
         scripCodes.add(11184);
       // scripCodes.add(7);

        List<VWAPRequest> requests =
                VWAPApiBuilder.buildRequests(scripCodes);

        // 🔵 Executes API calls in batches
        vwapaAPIExecutor.executeInBatches(requests, 50);
    }
}
