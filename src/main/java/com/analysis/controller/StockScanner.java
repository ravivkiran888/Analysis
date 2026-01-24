package com.analysis.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.analysis.dto.ScanResultDTO;
import com.analysis.services.StockScannerService;

@RestController
public class StockScanner {

	private final StockScannerService stockScannerService;

	public StockScanner(StockScannerService stockScannerService) {
		this.stockScannerService = stockScannerService;
	}

	@GetMapping("/show")
	public List<ScanResultDTO> showStocks() {
		
		
		return stockScannerService.scanStocks()
		        .stream()
		        .toList();

	}
}
