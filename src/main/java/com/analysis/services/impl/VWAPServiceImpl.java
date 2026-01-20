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
import com.analysis.repositories.VWAPValueRepository;
import com.analysis.services.VWAPService;

@Service
public class VWAPServiceImpl implements VWAPService {

    private final VWAPValueRepository repository;
    private final MongoTemplate mongoTemplate;

    public VWAPServiceImpl(
            VWAPValueRepository repository,
            MongoTemplate mongoTemplate) {
        this.repository = repository;
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Deletes ALL VWAP values once per job
     */
    @Override
    public void deleteEntryInVWAP(String scriptCode) {
    	repository.deleteByScripCode(scriptCode);
    }

    @Override
    public void saveOrUpdateVWAP(
    		String scripCode,
            String symbol,
            BigDecimal vwap,
            BigDecimal close,
            BigDecimal volume
    		) {

        Query query = new Query(
                Criteria.where(APPConstants.SYMBOL).is(symbol)
        );

        Update update = new Update()
                .set(APPConstants.SCRIPT_CODE, scripCode)
                .set(APPConstants.SYMBOL, symbol)
                .set("vwap", vwap)
                .set("close", close)
                .set("volume", volume)
                .set("updatedAt", Instant.now());

        mongoTemplate.upsert(
                query,
                update,
                VWAPValue.class,
                APPConstants.VWAP_VALUES_COLLECTION
        );
    }

}
