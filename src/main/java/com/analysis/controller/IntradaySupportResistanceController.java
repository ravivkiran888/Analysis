package com.analysis.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.analysis.dto.SupportResistanceSimpleResponseDTO;
import com.analysis.services.IntradaySupportResistanceService;

@RestController
@RequestMapping("/api")
public class IntradaySupportResistanceController {

    private final IntradaySupportResistanceService service;

    public IntradaySupportResistanceController(
            IntradaySupportResistanceService service) {
        this.service = service;
    }

    @GetMapping("/sr/{symbol}")
    public SupportResistanceSimpleResponseDTO getLevels(
            @PathVariable String symbol) {

        return service.calculate(symbol);
    }
}
