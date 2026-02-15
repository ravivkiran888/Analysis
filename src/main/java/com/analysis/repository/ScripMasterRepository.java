package com.analysis.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.analysis.documents.ScripMaster;

@Repository
public interface ScripMasterRepository extends MongoRepository<ScripMaster, String> {
    
    Optional<ScripMaster> findByScripCode(String scripCode);
    
    Optional<ScripMaster> findBySymbol(String symbol);
}