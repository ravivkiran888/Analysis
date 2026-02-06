package com.analysis.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.analysis.dto.PivotPointResponse;
import com.analysis.services.PivotPointCalculator;

@RestController
@RequestMapping("/api/pivot")
public class PivotPointController {
    
    private final PivotPointCalculator pivotCalculator;
    
    public PivotPointController(PivotPointCalculator pivotCalculator) {
        this.pivotCalculator = pivotCalculator;
    }
    
    @GetMapping("/{symbol}")
    public ResponseEntity<?> getPivotPoints(@PathVariable String symbol) {
        try {
            PivotPointResponse response = pivotCalculator.calculatePivotPoints(symbol);
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("Invalid symbol: " + symbol));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(new ErrorResponse("Error calculating pivot points: " + e.getMessage()));
        }
    }
}

/**
 * Error Response DTO
 */
 class ErrorResponse {
    private String error;
    
    public ErrorResponse(String error) {
        this.error = error;
    }
    
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}