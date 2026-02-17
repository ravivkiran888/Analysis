package com.analysis.controller;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.analysis.documents.SymbolIndicators;
import com.analysis.service.SignalService;

@RestController
@RequestMapping("/api/signals")
public class SignalController {

    private static final long MIN_VOLUME_THRESHOLD = 1_000_000L; // Constant

	
    private final SignalService signalService;

    public SignalController(SignalService signalService) {
        this.signalService = signalService;
    }

    @GetMapping("/ready")
    public List<SymbolIndicators> getEntryReadySymbols() {
        List<SymbolIndicators> allSymbols = signalService.getEntryReadySymbols();
        if (allSymbols == null) {
            return Collections.emptyList();
        }
        return allSymbols.stream()
                .filter(s -> s.getTotalDayVolume() >= MIN_VOLUME_THRESHOLD)
                .collect(Collectors.toList());
    }
    
    
}