package com.analysis.services;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.ConvertOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import com.analysis.dto.ScanResultDTO;

@Service
public class TrendScannerService {

    @Autowired
    private MongoTemplate mongoTemplate;

    
    public List<ScanResultDTO> getEligibleStocks() {

        Aggregation aggregation = Aggregation.newAggregation(

            // 1️⃣ Only actionable signals
            Aggregation.match(
                Criteria.where("signalState")
                        .in("WATCH", "ENTRY_READY")
            ),

            // 2️⃣ Join VWAP for live price context
            Aggregation.lookup(
                "vwap_values",
                "ScripCode",
                "ScripCode",
                "vwap"
            ),
            Aggregation.unwind("vwap"),

            Aggregation.project()
            .and("scripCode").as("scripCode")
            .and("symbol").as("symbol")
            .and("signalState").as("signalState")
            .and("vwap.close").as("close")
            .and("vwap.vwap").as("vwap")
            .and("vwap.updatedAt").as("updatedAt")
            .and("vwap.volume").as("volume"));
            


        return mongoTemplate
            .aggregate(aggregation, "signal_states", ScanResultDTO.class)
            .getMappedResults();
    }

    
   }
