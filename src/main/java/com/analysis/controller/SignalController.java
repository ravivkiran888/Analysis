package com.analysis.controller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.analysis.constants.Constants;
import com.analysis.documents.SymbolIndicators;
import com.analysis.dto.SectorIndicatorDTO;
import com.analysis.service.SectorIndicatorService;
import com.analysis.service.SignalService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/signals")
@RequiredArgsConstructor
public class SignalController {

	private static final long MIN_VOLUME_THRESHOLD = 1_000_000L;

	// Constant
	private final SectorIndicatorService sectorIndicatorService;
	private final SignalService signalService;


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

	private List<SymbolIndicators> prioritize(List<SymbolIndicators> all) {

		if (all == null || all.isEmpty()) {
			return Collections.emptyList();
		}

		int limit = 25;
		List<SymbolIndicators> result = new ArrayList<>(limit);

		for (SymbolIndicators s : all) {
			if (Constants.ENTRY_READY.equals(s.getSignal())) {
				result.add(s);
				if (result.size() == limit) {
					return result;
				}
			}
		}

		for (SymbolIndicators s : all) {
			if (Constants.WATCH.equals(s.getSignal())) {
				result.add(s);
				if (result.size() == limit) {
					return result;
				}
			}
		}

		return result;
	}

}