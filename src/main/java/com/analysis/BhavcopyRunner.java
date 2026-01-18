package com.analysis;


import java.util.Comparator;
import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.analysis.dto.BhavcopyLatestView;
import com.analysis.services.BhavcopyLatestService;

@Component
public class BhavcopyRunner implements CommandLineRunner {

    private final BhavcopyLatestService service;

    public BhavcopyRunner(BhavcopyLatestService service) {
        this.service = service;
    }

    @Override
    public void run(String... args) {
    	
    	/*

        System.out.println("Fetching latest bhavcopy data...");

        List<BhavcopyLatestView> results =
                service.fetchLatestBhavcopy();

        results.stream()
               .filter(r -> r.getTtlTradgVol() != null)
               .filter(r -> r.getTtlTradgVol() < 700000)
               .sorted(Comparator.comparingLong(BhavcopyLatestView::getTtlTradgVol))
               .map(BhavcopyLatestView::getSymbol)
               .distinct()
               .forEach(s -> System.out.print(s + " "));
*/
    }

}
