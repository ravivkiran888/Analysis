package com.analysis.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.analysis.documents.VWAPValue;
import com.analysis.dto.BhavCopy;
import com.analysis.dto.SupportResistanceLevelDTO;
import com.analysis.dto.SupportResistanceSimpleResponseDTO;
import com.analysis.repositories.BhavCopyRepository;
import com.analysis.repositories.VWAPValueRepository;

@Service
public class IntradaySupportResistanceService {

    // Zone width = ±0.15%
    private static final BigDecimal ZONE_PERCENT =
            new BigDecimal("0.0015");

    private final VWAPValueRepository vwapRepo;
    private final BhavCopyRepository bhavRepo;

    public IntradaySupportResistanceService(
            VWAPValueRepository vwapRepo,
            BhavCopyRepository bhavRepo) {

        this.vwapRepo = vwapRepo;
        this.bhavRepo = bhavRepo;
    }

    public SupportResistanceSimpleResponseDTO calculate(String symbol) {

        VWAPValue vwapValue = vwapRepo
                .findTopBySymbolOrderByUpdatedAtDesc(symbol)
                .orElseThrow(() ->
                        new IllegalArgumentException("VWAP not found"));

        BhavCopy bhav = bhavRepo
                .findTopByTckrSymbOrderByTradDtDesc(symbol)
                .orElseThrow(() ->
                        new IllegalArgumentException("Bhavcopy not found"));

        BigDecimal vwap = vwapValue.getVwap();
        BigDecimal pdh = BigDecimal.valueOf(bhav.getHghPric());
        BigDecimal pdl = BigDecimal.valueOf(bhav.getLwPric());

        BigDecimal vwapLower = vwap.multiply(new BigDecimal("0.995"));

        List<SupportResistanceLevelDTO> buyZones = new ArrayList<>();
        List<SupportResistanceLevelDTO> sellZones = new ArrayList<>();

        // ===============================
        // BUY ZONES
        // ===============================
        buyZones.add(zone("SUPPORT", "VWAP_LOWER", vwapLower, 4));
        buyZones.add(zone("SUPPORT", "PDL", pdl, 5));

        // ===============================
        // SELL ZONES
        // ===============================
        sellZones.add(zone("RESISTANCE", "VWAP", vwap, 5));
        sellZones.add(zone("RESISTANCE", "PDH", pdh, 5));

        return new SupportResistanceSimpleResponseDTO(
                symbol,
                buyZones,
                sellZones
        );
    }

    
    // =========================================================
    // ZONE BUILDER
    // =========================================================
    private SupportResistanceLevelDTO zone(
            String type,
            String source,
            BigDecimal price,
            int strength) {

        BigDecimal range = price.multiply(ZONE_PERCENT);

        BigDecimal from = price.subtract(range)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal to = price.add(range)
                .setScale(2, RoundingMode.HALF_UP);

        return new SupportResistanceLevelDTO(
                type,
                source,
                from,
                to,
                strength
        );
    }
}
