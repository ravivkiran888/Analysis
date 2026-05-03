package com.analysis.service;

import java.util.ArrayList;
import java.util.List;

import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.AddFieldsOperation;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.ArrayOperators;
import org.springframework.data.mongodb.core.aggregation.ComparisonOperators;
import org.springframework.data.mongodb.core.aggregation.ConditionalOperators;
import org.springframework.data.mongodb.core.aggregation.ConvertOperators;
import org.springframework.data.mongodb.core.aggregation.LookupOperation;
import org.springframework.data.mongodb.core.aggregation.MatchOperation;
import org.springframework.data.mongodb.core.aggregation.ProjectionOperation;
import org.springframework.data.mongodb.core.aggregation.SortOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import com.analysis.constants.Constants;
import com.analysis.documents.SymbolIndicators;

@Service
public class SignalService {

    private final MongoTemplate mongoTemplate;

    public SignalService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public List<SymbolIndicators> getEntryReadyOrWatchSymbols(String mode) {

        List<AggregationOperation> operations = new ArrayList<>();

        // Apply match only for specific modes
        if (mode != null && Constants.ENTRY_READY.equalsIgnoreCase(mode)) {
            MatchOperation match =
                    Aggregation.match(
                            Criteria.where(Constants.SIGNAL)
                                    .in(Constants.ENTRY_READY, Constants.WATCH)
                    );
            operations.add(match);
        }

        SortOperation sort =
                Aggregation.sort(Sort.by(Sort.Direction.DESC, "totalDayVolume"));

        LookupOperation lookup =
                LookupOperation.newLookup()
                        .from("optionChainIndicators")
                        .localField("symbol")
                        .foreignField("symbol")
                        .as("optionData");

        AddFieldsOperation addFields =
                AddFieldsOperation.addField("isOptionChain")
                        .withValue(
                                ConditionalOperators.when(
                                        ComparisonOperators.Gt.valueOf(
                                                ArrayOperators.Size.lengthOfArray("optionData")
                                        ).greaterThanValue(0)
                                ).then(true).otherwise(false)
                        ).build();

        ProjectionOperation project =
                Aggregation.project().andExclude("optionData");

        operations.add(sort);
        operations.add(lookup);
        operations.add(addFields);
        operations.add(project);

        Aggregation aggregation = Aggregation.newAggregation(operations);

        AggregationResults<SymbolIndicators> results =
                mongoTemplate.aggregate(
                        aggregation,
                        Constants.SYMBOL_INDICATORS_COLLECTION,
                        SymbolIndicators.class
                );

        return results.getMappedResults();
    }
    
    public List<Document> getSupportAndBreakoutStocks() {

        Aggregation aggregation = Aggregation.newAggregation(

            // 🔹 Convert fields
            Aggregation.addFields()
                .addFieldWithValue("ltp", ConvertOperators.ToDouble.toDouble("$lastTradedPrice"))
                .addFieldWithValue("prevLowD", ConvertOperators.ToDouble.toDouble("$prevLow"))
                .addFieldWithValue("prevHighD", ConvertOperators.ToDouble.toDouble("$prevHigh"))
                .addFieldWithValue("openD", ConvertOperators.ToDouble.toDouble("$dayOpen"))
                .addFieldWithValue("lowD", ConvertOperators.ToDouble.toDouble("$dayLow"))
                .addFieldWithValue("ema20D", ConvertOperators.ToDouble.toDouble("$ema20")) // ✅ still used for trend
                .build(),

            // 🔹 Match (NO EMA FILTER)
            context -> new Document("$match",
                new Document("$expr",
                    new Document("$or", List.of(

                        // ✅ Support Bounce
                        new Document("$and", List.of(
                            new Document("$gte", List.of("$ltp",
                                new Document("$multiply", List.of("$prevLowD", 0.99))
                            )),
                            new Document("$lte", List.of("$ltp",
                                new Document("$multiply", List.of("$prevLowD", 1.01))
                            )),
                            new Document("$gt", List.of("$ltp", "$openD"))
                        )),

                        // 🚀 Breakout
                        new Document("$and", List.of(
                            new Document("$gte", List.of("$ltp",
                                new Document("$multiply", List.of("$prevHighD", 1.001))
                            )),
                            new Document("$lte", List.of("$ltp",
                                new Document("$multiply", List.of("$prevHighD", 1.01))
                            ))
                        ))

                    ))
                )
            ),

            // 🔹 Category + Trend (trend is informational only)
            Aggregation.addFields()
                .addFieldWithValue("category",
                    new Document("$cond", List.of(

                        // BREAKOUT
                        new Document("$and", List.of(
                            new Document("$gte", List.of("$ltp",
                                new Document("$multiply", List.of("$prevHighD", 1.001))
                            )),
                            new Document("$lte", List.of("$ltp",
                                new Document("$multiply", List.of("$prevHighD", 1.01))
                            ))
                        )),

                        "BREAK_RESISTANCE",

                        // SUPPORT
                        new Document("$cond", List.of(
                            new Document("$and", List.of(
                                new Document("$gte", List.of("$ltp",
                                    new Document("$multiply", List.of("$prevLowD", 0.99))
                                )),
                                new Document("$lte", List.of("$ltp",
                                    new Document("$multiply", List.of("$prevLowD", 1.01))
                                )),
                                new Document("$gt", List.of("$ltp", "$openD"))
                            )),
                            "SUPPORT_BOUNCE",
                            "NEAR_SUPPORT"
                        ))
                    ))
                )

                // ✅ Keep trend (but NOT filtering)
                .addFieldWithValue("trend",
                    new Document("$cond", List.of(
                        new Document("$gt", List.of("$ltp", "$ema20D")),
                        "BULLISH",
                        "BEARISH"
                    ))
                )
                .build(),

            // 🔹 Projection
            Aggregation.project(
                    "symbol",
                    "lastTradedPrice",
                    "prevLowD",
                    "prevHighD",
                    "dayOpen",
                    "dayLow",
                    "ema20",     // for UI
                    "trend",     // for UI
                    "category",
                    "totalDayVolume",
                    "sector",
                    "timestamp"
            ),

            // 🔹 Sort by volume
            Aggregation.sort(Sort.by(Sort.Direction.DESC, "totalDayVolume"))
        );

        return mongoTemplate.aggregate(aggregation, "symbol_indicators", Document.class)
                            .getMappedResults();
    }
    
    }