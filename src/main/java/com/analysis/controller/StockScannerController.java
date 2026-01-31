package com.analysis.controller;

import java.util.List;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.analysis.SignalState;
import com.analysis.dto.ScanResultDTO;
import com.analysis.helper.NumberFormatter;
import com.analysis.services.TrendScannerService;

@CrossOrigin(origins = "*", maxAge = 3600)

@RestController
public class StockScannerController {

	private final TrendScannerService trendScannerService;

	public StockScannerController(TrendScannerService trendScannerService) {

		this.trendScannerService = trendScannerService;

	}
	@GetMapping("/ready")
	public List<ScanResultDTO> readyStocks() {
	    return getSortedStocksByState(SignalState.ENTRY_READY);
	}

	@GetMapping("/watch")
	public List<ScanResultDTO> watchStocks() {
	    return getSortedStocksByState(SignalState.WATCH);
	}


	 @GetMapping("/sai")
	    public String sai() {
	        return "Hello, Sai!";
	    }
	 
	  @GetMapping("/status")
	    public String status() {
	        return "Service is UP";
	    }


	/**
	 * Filters eligible stocks by signal state, sorts by volumeRatio descending,
	 * and formats volumes for user-friendly display.
	 */
	private List<ScanResultDTO> getSortedStocksByState(SignalState state) {

	    List<ScanResultDTO> stocks = trendScannerService.getEligibleStocks()
	        .stream()
	        .filter(e -> e.getSignalState() == state)
	        .sorted((a, b) -> b.getVolumeRatio().compareTo(a.getVolumeRatio()))
	        .toList(); // immutable list

	    // Format volumes for display
	    stocks.forEach(stock -> {
	        stock.setLastVolumeFormatted(NumberFormatter.formatLargeNumber(stock.getLastVolume()));
	        stock.setAvgVolume20Formatted(NumberFormatter.formatLargeNumber(stock.getAvgVolume20()));
	        
	        stock.setCurrentVolume(NumberFormatter.formatLargeNumber(stock.getVolume()));
	    });

	    return stocks;
	}



}
