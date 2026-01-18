package com.analysis.schedulers;

import java.time.LocalDate;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.analysis.helpers.FivePaisaApiClient;
import com.analysis.services.EMAService;
import com.analysis.services.ScripCache;

@Service
public class EMAScheduler {

    private final ScripCache scripCache;
    private final FivePaisaApiClient executor;
    private final EMAService emaService;

    public EMAScheduler(
            ScripCache scripCache,
            FivePaisaApiClient executor,
            EMAService emaService
    ) {
        this.scripCache = scripCache;
        this.executor = executor;
        this.emaService = emaService;
    }

  //  @Scheduled(cron = "0 45 8 * * MON-FRI", zone = "Asia/Kolkata")
    
    public void run() throws Exception {

        String from = LocalDate.now().minusDays(10).toString();
        String to   = LocalDate.now().minusDays(1).toString();

        for (Map.Entry<Integer, String> entry
                : scripCache.getAllScripEntries()) {

            int scripCode = entry.getKey();
            String symbol = entry.getValue();
            
                     
            String json =
                    executor.fetch30MinCandles(scripCode, from, to);

            emaService.processApiResponse(String.valueOf(scripCode) , symbol , json);

           

            Thread.sleep(60);
        }
    }

    
}
