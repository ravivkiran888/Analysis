package com.analysis;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.analysis.schedulers.EMAScheduler;
import com.analysis.schedulers.VWAPScheduler;

@Component
public class BhavcopyRunner implements CommandLineRunner {

    private final EMAScheduler emaScheduler;
    private final VWAPScheduler vwapScheduler;

    public BhavcopyRunner(
            EMAScheduler emaScheduler,
            VWAPScheduler vwapScheduler
    ) {
        this.emaScheduler = emaScheduler;
        this.vwapScheduler = vwapScheduler;
    }

    @Override
    public void run(String... args) {

        System.out.println("Start");

        try {
            // Run once at application startup if needed
           //  emaScheduler.run();
             vwapScheduler.runVWAPJob();

        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("End");
    }
}
