package com.analysis.runner;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;


import com.analysis.jobs.HistoricalDataJob;

@Component
public class HistoricalDataRunner implements CommandLineRunner {

    @Value("${runhistoricaldatascanner:false}")
    private boolean runhistoricaldatascanner;

    private final HistoricalDataJob historicalDataJob;

    public HistoricalDataRunner(HistoricalDataJob historicalDataJob) {
        this.historicalDataJob = historicalDataJob;
    }

    @Override
    public void run(String... args) {

        if (!runhistoricaldatascanner) {
            System.out.println("⛔ Historical scanner disabled");
            return;
        }

        System.out.println("🚀 Historical scanner started");

        long start = System.currentTimeMillis();

        historicalDataJob.run();

        long end = System.currentTimeMillis();

        System.out.println("✅ Historical scanner completed in " + (end - start) + " ms");
    }
}