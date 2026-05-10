package com.analysis.controller;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.analysis.constants.Constants;
import com.analysis.documents.SymbolIndicators;
import com.analysis.dto.PivotLevels;
import com.analysis.dto.SectorIndicatorDTO;
import com.analysis.service.OptionChainQueryService;
import com.analysis.service.PivotCalculatorService;
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
	private final OptionChainQueryService service;
	private final PivotCalculatorService pivotCalculatorService;
	private final MongoTemplate mongoTemplate;
	
	@GetMapping("/ready")
	public List<SymbolIndicators> getEntryReadySymbols() {
		
		  ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");

		    LocalTime BLOCK_START = LocalTime.of(9, 0);
		    LocalTime BLOCK_END = LocalTime.of(9, 35);

		    LocalTime now = LocalTime.now(IST_ZONE);

		    // Return empty list between 9:00 AM and 9:35 AM
		    if (!now.isBefore(BLOCK_START) && now.isBefore(BLOCK_END)) {
		        return Collections.emptyList();
		    }


		List<SymbolIndicators> allSymbols = signalService.getEntryReadyOrWatchSymbols(Constants.ENTRY_READY);
		if (allSymbols == null || allSymbols.isEmpty()) {
			return Collections.emptyList();
		}
		allSymbols = allSymbols.stream().filter(s -> s.getTotalDayVolume() >= MIN_VOLUME_THRESHOLD)
				.collect(Collectors.toList());
		return prioritize(allSymbols);
		
	}
	
	
	
	
	@GetMapping("/pivot/{symbol}")
    public ResponseEntity<?> getPivotLevels(
            @PathVariable String symbol
    ) {

        Query query = new Query();

        query.addCriteria(
                Criteria.where("symbol")
                        .regex("^" + symbol + "$", "i")
        );

        Document document = mongoTemplate.findOne(
                query,
                Document.class,
                "symbol_indicators"
        );

        if (document == null) {

            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body("Symbol not found");
        }

        String prevHigh = document.getString("prevHigh");
        String prevLow = document.getString("prevLow");
        String prevClose = document.getString("prevClose");
        String symbolDB = document.getString("symbol");

        PivotLevels levels =
                pivotCalculatorService.calculateLevels(
                        prevHigh,
                        prevLow,
                        prevClose,
                        symbolDB
                );

        return ResponseEntity.ok(levels);
    }
	
	
	

	@GetMapping("/sectors")
	public ResponseEntity<List<SectorIndicatorDTO>> getTopSectors() {
		List<SectorIndicatorDTO> topSectors = sectorIndicatorService.getTopSectorsByDayChange();
		return ResponseEntity.ok(topSectors);
	}
	
	
	
	

	@GetMapping("avgPrice")
	public List<SymbolIndicators> getAllAvgPrice() {

	    List<SymbolIndicators> allSymbols =
	            signalService.getEntryReadyOrWatchSymbols(null);

	    List<SymbolIndicators> filtered = allSymbols.stream()
	            .filter(e -> {
	 
	            	BigDecimal avg = e.getAvgPrice();
	                
	            	BigDecimal ltp = e.getLastTradedPrice();

	                if (avg == null || ltp == null) return false;

	                BigDecimal diff = ltp.subtract(avg);

	                return diff.compareTo(BigDecimal.valueOf(0.5)) >= 0 &&
	                       diff.compareTo(BigDecimal.valueOf(2)) <= 0;
	            })
	            .collect(Collectors.toList());

	    return filtered;
	}
	
	
	
	
	@GetMapping("/support-breakout")
    public List<Document> getSupportAndBreakoutStocks() {
        return signalService.getSupportAndBreakoutStocks();
    }
	
	

	@GetMapping("/options")
	public List<Document> getAllOptionChains() {

		return service.getAllOptionSymbols()
			    .stream()
			    .filter(e -> {
			        Number ltp = e.get("ltp", Number.class);
			        return ltp != null && ltp.doubleValue() <= 1500;
			    })
			    .collect(Collectors.toList());

	}

	private List<SymbolIndicators> prioritize(List<SymbolIndicators> all) {

		List<SymbolIndicators> result = new ArrayList<>();

		// First, collect and sort ENTRY_READY signals by volume
		List<SymbolIndicators> entryReadySignals = all.stream().filter(s -> Constants.ENTRY_READY.equals(s.getSignal()))
				.sorted((a, b) -> {
					long volumeA = a.getTotalDayVolume() != null ? a.getTotalDayVolume() : 0;
					long volumeB = b.getTotalDayVolume() != null ? b.getTotalDayVolume() : 0;
					return Long.compare(volumeB, volumeA); // Descending (highest volume first)
				}).collect(Collectors.toList());

		// Add all ENTRY_READY signals first
		result.addAll(entryReadySignals);

		// If we haven't reached LIMIT, add WATCH signals sorted by volume
		if (result.size() < LIMIT) {
			List<SymbolIndicators> watchSignals = all.stream().filter(s -> Constants.WATCH.equals(s.getSignal()))
					.sorted((a, b) -> {
						long volumeA = a.getTotalDayVolume() != null ? a.getTotalDayVolume() : 0;
						long volumeB = b.getTotalDayVolume() != null ? b.getTotalDayVolume() : 0;
						return Long.compare(volumeB, volumeA); // Descending (highest volume first)
					}).limit(LIMIT - result.size()).collect(Collectors.toList());

			result.addAll(watchSignals);
		}

		return result;
	}

}