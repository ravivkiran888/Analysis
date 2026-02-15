package com.analysis.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.analysis.documents.SymbolIndicators;
import com.analysis.service.SignalService;

@RestController
@RequestMapping("/api/signals")
public class SignalController {

    private final SignalService signalService;

    public SignalController(SignalService signalService) {
        this.signalService = signalService;
    }

    @GetMapping("/entry-ready")
    public List<SymbolIndicators> getEntryReadySymbols() {
        return signalService.getEntryReadySymbols();
    }
}