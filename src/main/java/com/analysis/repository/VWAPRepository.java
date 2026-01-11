
package com.analysis.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.analysis.documents.VWAPValue;

public interface VWAPRepository
        extends MongoRepository<VWAPValue, String> {

    Optional<VWAPValue> findBySymbol(String symbol);
}
