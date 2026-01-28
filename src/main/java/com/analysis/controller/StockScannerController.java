package com.analysis.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.analysis.dto.ScanResultDTO;
import com.analysis.services.EarlyScannerService;
import com.analysis.services.TrendScannerService;

@RestController
public class StockScannerController {

	private final TrendScannerService trendScannerService;
	
	private final EarlyScannerService earlyScannerService;

	public StockScannerController(TrendScannerService trendScannerService, EarlyScannerService earlyScannerService) {
		
		this.earlyScannerService = earlyScannerService;
		this.trendScannerService = trendScannerService;
	}

	@GetMapping("/trend")
	public List<ScanResultDTO> showStocks() {
		
		
		return trendScannerService.scanStocks()
		        .stream()
		        .filter(e ->
		            e.getVolume() != null &&
		            Long.parseLong(e.getVolume()) > 2_000_000
		        )
		        .toList();


	}
	
	 // NEW scanner (early entry)
    @GetMapping("/early")
    public List<ScanResultDTO> earlyBuySetup() {
       
    	return earlyScannerService.scanEarlyBuySetup()
		        .stream()
		        .filter(e ->
		            e.getVolume() != null &&
		            Long.parseLong(e.getVolume()) > 2_000_000
		        )
		        .toList();

    	
    	
    	
    }
	
}
