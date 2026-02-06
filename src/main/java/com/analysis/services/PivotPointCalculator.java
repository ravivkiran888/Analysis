package com.analysis.services;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import com.analysis.documents.BhavCopyDocument;
import com.analysis.dto.PivotPointResponse;
import com.analysis.dto.Resistances;
import com.analysis.dto.Supports;

@Service
public class PivotPointCalculator {

    private final MongoTemplate mongoTemplate;

    public PivotPointCalculator(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Calculates CLASSIC Pivot Points using previous day's OHLC
     */
    public PivotPointResponse calculatePivotPoints(String tckrSymb) {

        // 1️⃣ Fetch previous day bhavcopy data
        Query query = new Query(Criteria.where("TckrSymb").is(tckrSymb));
        BhavCopyDocument doc =
                mongoTemplate.findOne(query, BhavCopyDocument.class, "bhavcopy");

        if (doc == null) {
            throw new IllegalArgumentException(
                    "No bhavcopy data found for symbol: " + tckrSymb);
        }

        // 2️⃣ Extract OHLC
        BigDecimal high = BigDecimal.valueOf(doc.getHghPric());
        BigDecimal low  = BigDecimal.valueOf(doc.getLwPric());
        BigDecimal close = BigDecimal.valueOf(doc.getClsPric());

        // 3️⃣ Pivot Point (PP)
        BigDecimal pivotPoint = high
                .add(low)
                .add(close)
                .divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP);

        // Range = High − Low
        BigDecimal range = high.subtract(low);

        // 4️⃣ Classic Resistance Levels
        BigDecimal r1 = pivotPoint.multiply(BigDecimal.valueOf(2))
                .subtract(low)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal r2 = pivotPoint.add(range)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal r3 = pivotPoint.add(range.multiply(BigDecimal.valueOf(2)))
                .setScale(2, RoundingMode.HALF_UP);

        // 5️⃣ Classic Support Levels
        BigDecimal s1 = pivotPoint.multiply(BigDecimal.valueOf(2))
                .subtract(high)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal s2 = pivotPoint.subtract(range)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal s3 = pivotPoint.subtract(range.multiply(BigDecimal.valueOf(2)))
                .setScale(2, RoundingMode.HALF_UP);

        // 6️⃣ Response DTOs
        Resistances resistances = new Resistances(r1, r2, r3);
        Supports supports = new Supports(s1, s2, s3);

        return new PivotPointResponse(
                tckrSymb,
                doc.getTradDt(),
                pivotPoint,
                resistances,
                supports
        );
    }
}
