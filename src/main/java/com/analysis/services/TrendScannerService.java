package com.analysis.services;

import java.util.List;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.UnwindOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import com.analysis.dto.ScanResultDTO;

@Service
public class TrendScannerService {

    @Autowired
    private MongoTemplate mongoTemplate;

    public List<ScanResultDTO> getEligibleStocks() {

        UnwindOperation unwindVwap = Aggregation.unwind("vwap", true);
        UnwindOperation unwindMarketSnapshot = Aggregation.unwind("marketSnapshot", true);

        Aggregation aggregation = Aggregation.newAggregation(
            // Match only ENTRY_READY signals
            Aggregation.match(Criteria.where("signalState").in("ENTRY_READY")),

            // Join with vwap_values collection (only for updatedAt)
            Aggregation.lookup("vwap_values", "ScripCode", "ScripCode", "vwap"),
            unwindVwap,

            // Join with market_snapshots collection
            Aggregation.lookup("market_snapshots", "ScripCode", "ScripCode", "marketSnapshot"),
            unwindMarketSnapshot,

            // Project ONLY the fields you need
            Aggregation.project()
                // From signal_states
                .and("ScripCode").as("scripCode")
                .and("symbol").as("symbol")
                .and("signalState").as("signalState")
                .and("Sector").as("sector")
                
                // From vwap_values (ONLY updatedAt)
                .and("vwap.updatedAt").as("vwapUpdatedAt") // Renamed for clarity
                
                // From market_snapshots
                .and("marketSnapshot.open").as("open")
                .and("marketSnapshot.high").as("high")
                .and("marketSnapshot.low").as("low")
                .and("marketSnapshot.close").as("close")
                .and("marketSnapshot.volume").as("volume")
                .and("marketSnapshot.netChange").as("netChange")
                .and("marketSnapshot.lastTradedPrice").as("lastTradedPrice")
        );

        // Fetch results
        List<ScanResultDTO> results = mongoTemplate
            .aggregate(aggregation, "signal_states", ScanResultDTO.class)
            .getMappedResults();

        return results;
    }

}
