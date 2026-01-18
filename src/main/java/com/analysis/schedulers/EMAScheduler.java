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

    @Scheduled(cron = "0 20 * * * MON-FRI")
    public void run() throws Exception {

        String from = LocalDate.now().minusDays(14).toString();
        String to   = LocalDate.now().toString();

        Set<Integer> allScripts = Set.of(11184);
        // scripCache.getAllScripCodes();

        for (int scripCode : allScripts) {

            String json = executor.fetch60MinCandles(scripCode, from, to);
            emaService.processApiResponse(scripCode, json);

            Thread.sleep(120); // safe rate-limit buffer
        }
    }
}
