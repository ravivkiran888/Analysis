package com.analysis.repositories;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.analysis.documents.VWAPValue;

public interface VWAPValueRepository
        extends MongoRepository<VWAPValue, String> {
	
    void deleteByScripCode(String scripCode);
    Optional<VWAPValue> findTopBySymbolOrderByUpdatedAtDesc(String symbol);


}
