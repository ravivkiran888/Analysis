package com.analysis.controller;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.analysis.documents.StockLevelsDocument;
import com.analysis.documents.SymbolIndicators;
import com.analysis.dto.BuySignalDTO;
import com.analysis.service.SignalService;
import com.analysis.service.StockLevelsService;

@RestController
@RequestMapping("/api/signals")
public class SignalController {

    private static final long MIN_VOLUME_THRESHOLD = 1_000_000L; // Constant

    private final SignalService signalService;
    private final StockLevelsService stockLevelsService;

		public SignalController(SignalService signalService, StockLevelsService stockLevelsService) {
			this.signalService = signalService;
			this.stockLevelsService = stockLevelsService;
		}
    
    @GetMapping("/ready")
    public List<SymbolIndicators> getEntryReadySymbols() {
        List<SymbolIndicators> allSymbols = signalService.getEntryReadyOrWatchSymbols();
        if (allSymbols == null) {
            return Collections.emptyList();
        }
        return allSymbols.stream()
                .filter(s -> s.getTotalDayVolume() >= MIN_VOLUME_THRESHOLD)
                .collect(Collectors.toList());
    }
    
    
    @GetMapping("/tobuy")
    public List<BuySignalDTO> getBuySignals(
            @RequestParam(defaultValue = "1.3") double minPressureRatio,
            @RequestParam(defaultValue = "true") boolean includeBreakout,
            @RequestParam(defaultValue = "30") int minSupportStrength) {
        return stockLevelsService.findBuySignals(minPressureRatio, includeBreakout, minSupportStrength);
    }   

    @GetMapping("/{symbol}")
    public ResponseEntity<StockLevelsDocument> getStockLevels(@PathVariable String symbol) {
    	
    	
        return stockLevelsService.findBySymbol(symbol.toUpperCase())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
}