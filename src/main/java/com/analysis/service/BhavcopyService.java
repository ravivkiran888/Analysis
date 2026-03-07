package com.analysis.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import com.analysis.constants.Constants;
import com.analysis.documents.Bhavcopy;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BhavcopyService {

    private final MongoTemplate mongoTemplate;
    private static final int FIVE_MIN_CANDLES_PER_DAY = 75; 

    public Bhavcopy getBhavcopyBySymbol(String symbol) {
        Query query = new Query(Criteria.where(Constants.TCKRSYMB).is(symbol));
        return mongoTemplate.findOne(query, Bhavcopy.class);
    }

    public BigDecimal getAvgVolumePer5Min(Bhavcopy bhav) {
        if (bhav == null || bhav.getTtlTradgVol() == null) return BigDecimal.ZERO;
        return BigDecimal.valueOf(bhav.getTtlTradgVol())
                .divide(BigDecimal.valueOf(FIVE_MIN_CANDLES_PER_DAY), 2, RoundingMode.HALF_UP);
    }
}