package com.analysis.services;

import java.util.List;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.ArithmeticOperators;
import org.springframework.data.mongodb.core.aggregation.ComparisonOperators;
import org.springframework.data.mongodb.core.aggregation.ConvertOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import com.analysis.dto.ScanResultDTO;

@Service
public class EarlyScannerService  {

    private final MongoTemplate mongoTemplate;

    public EarlyScannerService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

       public List<ScanResultDTO> scanEarlyBuySetup() {

        Aggregation aggregation = Aggregation.newAggregation(

            Aggregation.project("ScripCode", "Symbol"),

            // VWAP
            Aggregation.lookup("vwap_values", "ScripCode", "ScripCode", "vwap"),
            Aggregation.unwind("vwap"),

            // Close near VWAP (reclaim / compression)
            Aggregation.match(
                Criteria.expr(
                    ComparisonOperators.Gte.valueOf(
                        ConvertOperators.ToDecimal.toDecimal("$vwap.close")
                    ).greaterThanEqualTo(
                        ArithmeticOperators.Multiply
                            .valueOf(ConvertOperators.ToDecimal.toDecimal("$vwap.vwap"))
                            .multiplyBy(0.998)
                    )
                )
            ),

            // EMA
            Aggregation.lookup("ema_30m", "ScripCode", "ScripCode", "ema"),
            Aggregation.unwind("ema"),

            // EMA20 close to EMA50 (pre-crossover)
            Aggregation.match(
                Criteria.expr(
                    ComparisonOperators.Gte.valueOf(
                        ConvertOperators.ToDecimal.toDecimal("$ema.ema20")
                    ).greaterThanEqualTo(
                        ArithmeticOperators.Multiply
                            .valueOf(ConvertOperators.ToDecimal.toDecimal("$ema.ema50"))
                            .multiplyBy(0.995)
                    )
                )
            ),

            // Early volume participation
            Aggregation.match(
                Criteria.expr(
                    ComparisonOperators.Gt.valueOf(
                        ConvertOperators.ToDecimal.toDecimal("$ema.lastVolume")
                    ).greaterThan(
                        ArithmeticOperators.Multiply
                            .valueOf(ConvertOperators.ToDecimal.toDecimal("$ema.avgVolume20"))
                            .multiplyBy(1.3)
                    )
                )
            ),

            // RSI
            Aggregation.lookup("rsi_values", "ScripCode", "ScripCode", "rsi"),
            Aggregation.unwind("rsi"),

            // Neutral-to-bullish RSI (avoid overextended)
            Aggregation.match(
                new Criteria().andOperator(
                    Criteria.expr(
                        ComparisonOperators.Gte.valueOf(
                            ConvertOperators.ToDecimal.toDecimal("$rsi.rsi")
                        ).greaterThanEqualToValue(38)
                    ),
                    Criteria.expr(
                        ComparisonOperators.Lte.valueOf(
                            ConvertOperators.ToDecimal.toDecimal("$rsi.rsi")
                        ).lessThanEqualToValue(55)
                    )
                )
            ),

            Aggregation.project()
            .and("ScripCode").as("scripCode")
            .and("Symbol").as("symbol")
            .and("vwap.close").as("close")
            .and("ema.lastVolume").as("volume")
            .and("ema.ema20").as("ema20")
            .and("ema.ema50").as("ema50")

            
            
        );

        return mongoTemplate
                .aggregate(aggregation, "scrip_symbol_eq", ScanResultDTO.class)
                .getMappedResults();
    }
}
