package com.analysis.schedulers;

import java.time.LocalDate;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import com.analysis.helpers.FivePaisaApiClient;
import com.analysis.services.EMAService;
import com.analysis.services.ScripCache;

@Service
public class EMAScheduler {

	private static final Logger log = LoggerFactory.getLogger(EMAScheduler.class);

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

    @Scheduled(cron = "0 0 20 * * *", zone = "Asia/Kolkata")
    public void run() {

        String from = LocalDate.now().minusDays(10).toString();
        String to   = LocalDate.now().minusDays(1).toString();

        log.info("EMA Scheduler started. Date range: {} to {}", from, to);

        
        for (Map.Entry<Integer, String> entry : scripCache.getAllScripEntries()) {

            int scripCode = entry.getKey();
            String symbol = entry.getValue();

            System.out.println(symbol);

            try {
                String json =
                        executor.fetch30MinCandles(scripCode, from, to);

                emaService.processApiResponse(
                        String.valueOf(scripCode),
                        symbol,
                        json
                );

            } catch (HttpClientErrorException.TooManyRequests ex) {
             	log.warn("Rate limit hit for {} ({}). Sleeping for 3 seconds.",
                        symbol, scripCode);

            	try {
					Thread.sleep(3000);
				} catch (InterruptedException e) {
				}
                continue;
            	
            } catch (Exception ex) {

            	 log.error("Failed processing EMA for {} ({})",
                         symbol, scripCode, ex);
            }
        }
    }

    
}
