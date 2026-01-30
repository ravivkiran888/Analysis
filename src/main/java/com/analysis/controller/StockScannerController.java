package com.analysis.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.analysis.dto.IntradayLongResult;
import com.analysis.dto.ScanResultDTO;
import com.analysis.services.EarlyScannerService;
import com.analysis.services.TrendScannerService;
import com.analysis.services.impl.IntradayScannerService;

@RestController
public class StockScannerController {

	private final TrendScannerService trendScannerService;
	
	private final EarlyScannerService earlyScannerService;
	
	
  private final IntradayScannerService scannerService;    
    

	public StockScannerController(TrendScannerService trendScannerService, EarlyScannerService earlyScannerService , IntradayScannerService scannerService) {
		
		this.earlyScannerService = earlyScannerService;
		this.trendScannerService = trendScannerService;
		this.scannerService = scannerService;
	}

	@GetMapping("/trend")
	public List<ScanResultDTO> showStocks() {
		
		
		return trendScannerService.scanStocks()
		        .stream()
		        .filter(e ->
		            e.getVolume() != null &&
		            Long.parseLong(e.getVolume()) > 50000
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
		            Long.parseLong(e.getVolume()) > 50000
		        )
		        .toList();

    	
    	
    	
    }
    
    
    @GetMapping("/longs")
    public List<IntradayLongResult> getLongSignals() {
        return scannerService.scanAllLongs();
    }
	
}
