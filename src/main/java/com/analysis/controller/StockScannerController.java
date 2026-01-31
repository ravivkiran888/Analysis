package com.analysis.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.analysis.SignalState;
import com.analysis.dto.ScanResultDTO;
import com.analysis.services.TrendScannerService;

@RestController
public class StockScannerController {

	private final TrendScannerService trendScannerService;
	
    

	public StockScannerController(TrendScannerService trendScannerService) {
		
		this.trendScannerService = trendScannerService;
		
	}

	@GetMapping("/ready")
	public List<ScanResultDTO> readyStocks() {
	    return trendScannerService.getEligibleStocks()
	        .stream()
	        .filter(e -> e.getSignalState() == SignalState.ENTRY_READY)
	        .toList();
	}

	
	
	@GetMapping("/watch")
	public List<ScanResultDTO> watchStocks() {
	    return trendScannerService.getEligibleStocks()
	        .stream()
	        .filter(e -> e.getSignalState() == SignalState.WATCH)
	        .toList();
	}

}
