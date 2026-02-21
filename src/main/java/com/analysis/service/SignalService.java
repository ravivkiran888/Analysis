package com.analysis.service;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import com.analysis.constants.Constants;
import com.analysis.documents.SymbolIndicators;

@Service
public class SignalService {

    private final MongoTemplate mongoTemplate;

    public SignalService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Fetches symbols with ENTRY_READY or WATCH signals, sorted by volumeExpansion and totalDayVolume
     */
    public List<SymbolIndicators> getEntryReadyOrWatchSymbols() {

        // Create query to fetch SymbolIndicators with ENTRY_READY or WATCH signals
        Query query = new Query(
                Criteria.where(Constants.SIGNAL)
                        .in(Constants.ENTRY_READY, Constants.WATCH)
        );

        // Apply sorting
        query.with(Sort.by(
                Sort.Order.desc("volumeExpansion"),
                Sort.Order.desc("totalDayVolume")
        ));

        // Fetch directly from symbol_indicators collection
        List<SymbolIndicators> indicators =
                mongoTemplate.find(query, SymbolIndicators.class, "symbol_indicators");

  
        return indicators;
    }

 
 
 
}