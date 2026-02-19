package com.analysis.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import com.analysis.documents.StockLevelsDocument;
import com.analysis.dto.BuySignalDTO;

@Service
public class StockLevelsService {

    @Autowired
    private MongoTemplate mongoTemplate;

    /**
     * Find stocks that are bullish based on:
     * - market_bias = "Bullish"
     * - OR pressure_ratio >= minPressureRatio
     * - OR breakout_signal = true (if includeBreakout)
     * Additionally, active_support and active_resistance must exist and be non‑null,
     * and support_strength >= minSupportStrength.
     */
    public List<BuySignalDTO> findBuySignals(double minPressureRatio, boolean includeBreakout, int minSupportStrength) {
        // Build OR conditions
        List<Criteria> orCriteria = new ArrayList<>();
        orCriteria.add(Criteria.where("market_bias").is("Bullish"));
        orCriteria.add(Criteria.where("pressure_ratio").gte(minPressureRatio));

        if (includeBreakout) {
            orCriteria.add(Criteria.where("breakout_signal").is(true));
        }

        // Safety guard: ensure at least one OR condition exists
        if (orCriteria.isEmpty()) {
            throw new IllegalStateException("At least one bullish condition must be specified");
        }

        // Construct final criteria in a clean, readable way
        Criteria criteria = new Criteria()
                .orOperator(orCriteria.toArray(new Criteria[0]))
                .and("active_support").ne(null)
                .and("active_resistance").ne(null)
                .and("support_strength").gte(minSupportStrength);

        Query query = new Query(criteria);
        query.fields()
                .include("symbol")
                .include("ltp")
                .include("market_bias")
                .include("pressure_ratio")
                .include("breakout_signal")
                .include("support_strength")
                .include("resistance_strength")
                .include("atm_pcr")
                .include("active_support")
                .include("active_resistance");

        List<StockLevelsDocument> results = mongoTemplate.find(query, StockLevelsDocument.class, "stock_levels");

        return results.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    
    /**
     * Find a single stock by its symbol (ID).
     */
    public Optional<StockLevelsDocument> findBySymbol(String symbol) {
        return Optional.ofNullable(mongoTemplate.findById(symbol, StockLevelsDocument.class, "stock_levels"));
    }
    
    private BuySignalDTO toDto(StockLevelsDocument doc) {
        BuySignalDTO dto = new BuySignalDTO();
        dto.setSymbol(doc.getSymbol());
        dto.setLtp(doc.getLtp());
        dto.setMarketBias(doc.getMarketBias());
        dto.setPressureRatio(doc.getPressureRatio());
        dto.setBreakoutSignal(doc.isBreakoutSignal());
        dto.setSupportStrength(doc.getSupportStrength());
        dto.setResistanceStrength(doc.getResistanceStrength());
        dto.setAtmPcr(doc.getAtmPcr());

        if (doc.getActiveSupport() != null) {
            dto.setSupportPrice(doc.getActiveSupport().getPrice());
            dto.setSupportDistancePercent(doc.getActiveSupport().getDistancePercent());
        }
        if (doc.getActiveResistance() != null) {
            dto.setResistancePrice(doc.getActiveResistance().getPrice());
            dto.setResistanceDistancePercent(doc.getActiveResistance().getDistancePercent());
        }
        return dto;
    }
}