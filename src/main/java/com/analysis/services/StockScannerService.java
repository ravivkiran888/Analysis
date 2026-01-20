package com.analysis.services;

import java.util.List;

import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.ComparisonOperators;
import org.springframework.data.mongodb.core.aggregation.ConvertOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import com.analysis.dto.ScanResultDTO;

@Service
public class StockScannerService {

    @Autowired
    private MongoTemplate mongoTemplate;

    public List<ScanResultDTO> scanStocks() {

        Aggregation aggregation = Aggregation.newAggregation(

            // Base collection
            Aggregation.project("ScripCode", "Symbol"),

            // ---------------- VWAP ----------------
            Aggregation.lookup(
                    "vwap_values",
                    "ScripCode",
                    "ScripCode",
                    "vwap"
            ),
            Aggregation.unwind("vwap"),

            // close > vwap
            Aggregation.match(
                Criteria.expr(
                    ComparisonOperators.Gt.valueOf(
                        ConvertOperators.ToDecimal.toDecimal("$vwap.close")
                    ).greaterThan(
                        ConvertOperators.ToDecimal.toDecimal("$vwap.vwap")
                    )
                )
            ),

            // ---------------- EMA ----------------
            Aggregation.lookup(
                    "ema_30m",
                    "ScripCode",
                    "ScripCode",
                    "ema"
            ),
            Aggregation.unwind("ema"),

            // ema20 > ema50 AND volumeExpansion = true
            Aggregation.match(
                new Criteria().andOperator(
                    Criteria.expr(
                        ComparisonOperators.Gt.valueOf(
                            ConvertOperators.ToDecimal.toDecimal("$ema.ema20")
                        ).greaterThan(
                            ConvertOperators.ToDecimal.toDecimal("$ema.ema50")
                        )
                    ),
                    Criteria.where("ema.volumeExpansion").is(true)
                )
            ),

            // ---------------- RSI ----------------
            Aggregation.lookup(
                    "rsi_values",
                    "ScripCode",
                    "ScripCode",
                    "rsi"
            ),
            Aggregation.unwind("rsi"),

            // rsi > 40
            Aggregation.match(
                Criteria.expr(
                    ComparisonOperators.Gt.valueOf(
                        ConvertOperators.ToDecimal.toDecimal("$rsi.rsi")
                    ).greaterThanValue(40)
                )
            ),

            // ---------------- FINAL OUTPUT ----------------
            Aggregation.project()
                .and("ScripCode").as("scripCode")
                .and("Symbol").as("symbol")
                .and("vwap.close").as("close")
                .and("vwap.volume").as("volume")
        );

        return mongoTemplate
                .aggregate(aggregation, "scrip_symbol_eq", ScanResultDTO.class)
                .getMappedResults();
    }
}
