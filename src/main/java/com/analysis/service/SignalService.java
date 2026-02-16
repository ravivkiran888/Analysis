package com.analysis.service;

import com.analysis.constants.Constants;
import com.analysis.documents.SymbolIndicators;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SignalService {

    private final MongoTemplate mongoTemplate;

    public SignalService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public List<SymbolIndicators> getEntryReadySymbols() {
        Query query = new Query(Criteria.where(Constants.SIGNAL).is(Constants.ENTRY_READY));
      
        query.with(Sort.by(
        	    Sort.Order.desc("volumeExpansion"),  // recent momentum first
        	    Sort.Order.desc("totalDayVolume")    // then liquidity
        	));
        
        return mongoTemplate.find(query, SymbolIndicators.class);
    }
}