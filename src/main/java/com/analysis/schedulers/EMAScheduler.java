package com.analysis.schedulers;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import org.springframework.scheduling.annotation.Scheduled;
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

    @Scheduled(cron = "0 45 8 * * MON-FRI", zone = "Asia/Kolkata")
    public void run() throws Exception {

        String from = LocalDate.now().minusDays(10).toString();
        String to   = LocalDate.now().minusDays(1).toString();

        Set<Integer> allScripts =
                new HashSet<>(scripCache.getAllScripCodes());

        for (int scripCode : allScripts) {

            String json =
                    executor.fetch30MinCandles(scripCode, from, to);

            emaService.processApiResponse(scripCode, json);

            Thread.sleep(120); // rate-limit safety
        }
    }
}
