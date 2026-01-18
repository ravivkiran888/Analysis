package com.analysis.services;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.stereotype.Service;

import com.analysis.dto.BhavcopyLatestView;

@Service
public class BhavcopyLatestService {

    private final MongoTemplate mongoTemplate;

    public BhavcopyLatestService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public List<BhavcopyLatestView> fetchLatestBhavcopy() {

        Aggregation aggregation = Aggregation.newAggregation(

            // Join with bhavcopy
            Aggregation.lookup(
                "bhavcopy",
                "Symbol",
                "TckrSymb",
                "bhav"
            ),

            // Flatten bhavcopy rows
            Aggregation.unwind("bhav"),

            // Sort so latest TradDt comes first
            Aggregation.sort(
                Sort.by(Sort.Direction.DESC, "bhav.TradDt")
            ),

            // Pick only latest bhavcopy per symbol
            Aggregation.group("Symbol")
                .first("ScripCode").as("scripCode")
                .first("Symbol").as("symbol")
                .first("bhav.OpnPric").as("opnPric")
                .first("bhav.TtlTradgVol").as("ttlTradgVol"),

            // Final projection
            Aggregation.project()
                .andExclude("_id")
                .and("scripCode").as("scripCode")
                .and("symbol").as("symbol")
                .and("opnPric").as("opnPric")
                .and("ttlTradgVol").as("ttlTradgVol")
        );

        return mongoTemplate
                .aggregate(
                    aggregation,
                    "scrip_symbol_eq",
                    BhavcopyLatestView.class
                )
                .getMappedResults();
    }
}
