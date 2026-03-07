package com.analysis.controller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.analysis.constants.Constants;
import com.analysis.documents.SymbolIndicators;
import com.analysis.dto.OptionChainIndicators;
import com.analysis.dto.SectorIndicatorDTO;
import com.analysis.service.OptionChainIndicatorsService;
import com.analysis.service.SectorIndicatorService;
import com.analysis.service.SignalService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/signals")
@RequiredArgsConstructor
public class SignalController {

	private static final long MIN_VOLUME_THRESHOLD = 1_000_000L;
	private static final long LIMIT = 30;


	// Constant
	private final SectorIndicatorService sectorIndicatorService;
	private final SignalService signalService;
    private final OptionChainIndicatorsService service;


	@GetMapping("/ready")
	public List<SymbolIndicators> getEntryReadySymbols() {

	    List<SymbolIndicators> allSymbols =
	            signalService.getEntryReadyOrWatchSymbols();

	    if (allSymbols == null || allSymbols.isEmpty()) {
	        return Collections.emptyList();
	    }
	    
	   
	    allSymbols = allSymbols.stream()
	            .filter(s -> s.getTotalDayVolume() >= MIN_VOLUME_THRESHOLD)
	            .collect(Collectors.toList());

	    return prioritize(allSymbols);
	}
	
	@GetMapping("/sectors")
	public ResponseEntity<List<SectorIndicatorDTO>> getTopSectors() {
		List<SectorIndicatorDTO> topSectors = sectorIndicatorService.getTopSectorsByDayChange();
		return ResponseEntity.ok(topSectors);
	}
	
	
	
	 @GetMapping("/{symbol}")
	    public OptionChainIndicators getOptionMetrics(@PathVariable String symbol) {

	        return service.getBySymbol(symbol);
	    }

	private List<SymbolIndicators> prioritize(List<SymbolIndicators> all) {
		
		List<SymbolIndicators> result = new ArrayList<>();

		// First, collect and sort ENTRY_READY signals by volume
		List<SymbolIndicators> entryReadySignals = all.stream()
		    .filter(s -> Constants.ENTRY_READY.equals(s.getSignal()))
		    .sorted((a, b) -> {
		        long volumeA = a.getTotalDayVolume() != null ? a.getTotalDayVolume() : 0;
		        long volumeB = b.getTotalDayVolume() != null ? b.getTotalDayVolume() : 0;
		        return Long.compare(volumeB, volumeA); // Descending (highest volume first)
		    })
		    .collect(Collectors.toList());

		// Add all ENTRY_READY signals first
		result.addAll(entryReadySignals);

		// If we haven't reached LIMIT, add WATCH signals sorted by volume
		if (result.size() < LIMIT) {
		    List<SymbolIndicators> watchSignals = all.stream()
		        .filter(s -> Constants.WATCH.equals(s.getSignal()))
		        .sorted((a, b) -> {
		            long volumeA = a.getTotalDayVolume() != null ? a.getTotalDayVolume() : 0;
		            long volumeB = b.getTotalDayVolume() != null ? b.getTotalDayVolume() : 0;
		            return Long.compare(volumeB, volumeA); // Descending (highest volume first)
		        })
		        .limit(LIMIT - result.size())
		        .collect(Collectors.toList());
		    
		    result.addAll(watchSignals);
		}

		return result;
	}

}