package com.analysis.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.analysis.model.RSIValue;

public interface RSIValueRepository
        extends MongoRepository<RSIValue, String> {

    Optional<RSIValue>
    findTopByScripCodeAndPeriodOrderByCalculatedAtDesc(
            int scripCode,
            int period);
    
    
    void deleteByScripCode(
            String scripCode);
}
