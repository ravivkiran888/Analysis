package com.analysis.controller;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
        List<SymbolIndicators> allSymbols = signalService.getEntryReadyOrWatchSymbols();
        if (allSymbols == null) {
            return Collections.emptyList();
        }
        return allSymbols.stream()
                .filter(s -> s.getTotalDayVolume() >= MIN_VOLUME_THRESHOLD)
                .collect(Collectors.toList());
    }
    
    
    
    @GetMapping("/sectors")
    public ResponseEntity<List<SectorIndicatorDTO>> getTopSectors() {
        List<SectorIndicatorDTO> topSectors = sectorIndicatorService.getTopSectorsByDayChange();
        return ResponseEntity.ok(topSectors);
    }
    		
}