package com.analysis.services;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.UnwindOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import com.analysis.dto.ScanResultDTO;
import com.analysis.helper.SignalHelper;

@Service
public class TrendScannerService {

    @Autowired
    private MongoTemplate mongoTemplate;

    public List<ScanResultDTO> getEligibleStocks() {

        UnwindOperation unwindVwap = Aggregation.unwind("vwap", true);
        UnwindOperation unwindEma = Aggregation.unwind("ema", true);

        Aggregation aggregation = Aggregation.newAggregation(
            Aggregation.match(Criteria.where("signalState").in("WATCH", "ENTRY_READY")),

            Aggregation.lookup("vwap_values", "ScripCode", "ScripCode", "vwap"),
            unwindVwap,

            Aggregation.lookup("ema_30m", "ScripCode", "ScripCode", "ema"),
            unwindEma,

            Aggregation.project()
                .and("ScripCode").as("scripCode")
                .and("symbol").as("symbol")
                .and("signalState").as("signalState")
                .and("vwap.close").as("close")
                .and("vwap.vwap").as("vwap")
                .and("vwap.volume").as("volume")
                .and("vwap.updatedAt").as("updatedAt") 
                .and("ema.ema20").as("ema20")
                .and("ema.ema50").as("ema50")
                .and("ema.lastVolume").as("lastVolume")
                .and("ema.avgVolume20").as("avgVolume20")
        );

        // Fetch raw results
        List<ScanResultDTO> results = mongoTemplate
            .aggregate(aggregation, "signal_states", ScanResultDTO.class)
            .getMappedResults();

        // Compute derived fields in Java
        results.forEach(SignalHelper::computeDerivedFields);

        return results;
    }

}
