
package com.analysis.controller;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.analysis.SignalState;
import com.analysis.dto.ScanResultDTO;
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



	 @GetMapping("/sai")
	    public String sai() {
	        return "Hello, Sai!";
	    }
	 
	  @GetMapping("/status")
	    public String status() {
	        return "Service is UP";
	    }


	
	  private List<ScanResultDTO> getSortedStocksByState(SignalState state) {
		    Comparator<ScanResultDTO> volumeSorter = (resOne, resTwo) -> 
		        Double.compare(resTwo.getVolume(), resOne.getVolume()); // Descending by volume
		    
		    // Get the stocks and create a mutable copy
		    List<ScanResultDTO> stocks = new ArrayList<>(trendScannerService.getEligibleStocks());
		    
		    // Sort by volume (highest first)
		    stocks.sort(volumeSorter);
		    
		    return stocks;
		}


}
