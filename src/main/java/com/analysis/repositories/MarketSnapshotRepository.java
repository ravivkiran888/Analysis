package com.analysis.repositories;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.analysis.documents.MarketSnapshot;

public interface MarketSnapshotRepository extends MongoRepository<MarketSnapshot, String> {
    
    MarketSnapshot findByScripCode(String scripCode);
}