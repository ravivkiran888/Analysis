package com.analysis.services.impl;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.analysis.APPConstants;
import com.analysis.documents.VWAPValue;
import com.analysis.services.VWAPService;


@Service
public class VWAPServiceImpl implements VWAPService {

    private final MongoTemplate mongoTemplate;

    public VWAPServiceImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void saveOrUpdateVWAP(
            String scripCode,
            String symbol,
            BigDecimal vwap,
            BigDecimal close,
            BigDecimal volume) {

        // ✅ MUST match unique index
        Query query = new Query(
                Criteria.where(APPConstants.SCRIPT_CODE).is(scripCode)
        );

        Update update = new Update()
                .set(APPConstants.SYMBOL, symbol)
                .set("vwap", vwap)
                .set("close", close)
                .set("volume", volume)
                .set("updatedAt", Instant.now())
                .setOnInsert(APPConstants.SCRIPT_CODE, scripCode);

        mongoTemplate.upsert(
                query,
                update,
                VWAPValue.class,
                APPConstants.VWAP_VALUES_COLLECTION
        );
    }

}
