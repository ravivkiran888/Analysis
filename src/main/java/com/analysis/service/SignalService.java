package com.analysis.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.AddFieldsOperation;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.ArrayOperators;
import org.springframework.data.mongodb.core.aggregation.ComparisonOperators;
import org.springframework.data.mongodb.core.aggregation.ConditionalOperators;
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
 
}