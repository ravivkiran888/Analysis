package com.analysis.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import com.analysis.constants.Constants;
import com.analysis.documents.StockLevelsDocument;
import com.analysis.documents.SymbolIndicators;

@Service
public class SignalService {

    private final MongoTemplate mongoTemplate;

    public SignalService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

   
    public List<SymbolIndicators> getEntryReadyOrWatchSymbols() {

        // 1️⃣ Fetch SymbolIndicators
        Query query = new Query(
                Criteria.where(Constants.SIGNAL)
                        .in(Constants.ENTRY_READY, Constants.WATCH)
        );

        query.with(Sort.by(
                Sort.Order.desc("volumeExpansion"),
                Sort.Order.desc("totalDayVolume")
        ));

        List<SymbolIndicators> indicators =
                mongoTemplate.find(query, SymbolIndicators.class, "symbol_indicators");

        if (indicators.isEmpty()) {
            return indicators;
        }

        // 2️⃣ Extract symbols
        List<String> symbols = indicators.stream()
                .map(SymbolIndicators::getSymbol)
                .distinct()
                .collect(Collectors.toList());

        // 3️⃣ Fetch stock_levels in one query
        Query levelsQuery = new Query(Criteria.where("symbol").in(symbols));

        List<StockLevelsDocument> stockLevels =
                mongoTemplate.find(levelsQuery, StockLevelsDocument.class, "stock_levels");

        // 4️⃣ Map symbol -> description
        Map<String, String> descriptionMap = stockLevels.stream()
                .filter(level -> level.getDescription() != null)
                .collect(Collectors.toMap(
                		StockLevelsDocument::getSymbol,
                		StockLevelsDocument::getDescription
                ));

        // 5️⃣ Inject description into existing objects
        indicators.forEach(indicator ->
                indicator.setDescription(
                        descriptionMap.getOrDefault(indicator.getSymbol(), null)
                )
        );

        return indicators;
    }

    
}