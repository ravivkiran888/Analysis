package com.analysis.runner;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.analysis.dto.ScanResultDTO;
import com.analysis.services.StockScannerService;

@Component
public class StockScannerRunner implements CommandLineRunner {

	@Value("${runscanner:false}")
	private boolean runscanner;

	@Autowired
	private final StockScannerService stockScannerService;

	public StockScannerRunner(StockScannerService stockScannerService

	) {
		this.stockScannerService = stockScannerService;

	}

	@Override
	public void run(String... args) {

		try {

			if (runscanner) {
				List<ScanResultDTO> results = stockScannerService.scanStocks();
				results.forEach(res -> {

					System.out.println(res.getSymbol() + "\t" + res.getClose() + "\t" + res.getVolume());
				});
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

	}
}
