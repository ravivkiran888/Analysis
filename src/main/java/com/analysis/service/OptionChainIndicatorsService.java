package com.analysis.service;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import com.analysis.dto.OptionChainIndicators;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OptionChainIndicatorsService {

    private final MongoTemplate mongoTemplate;

    public OptionChainIndicators getBySymbol(String symbol) {

        Query query = new Query();
        query.addCriteria(Criteria.where("symbol").is(symbol));

        return mongoTemplate.findOne(query, OptionChainIndicators.class, "optionChainIndicators");
    }
}