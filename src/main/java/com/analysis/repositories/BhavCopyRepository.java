package com.analysis.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.analysis.dto.BhavCopy;

public interface BhavCopyRepository
        extends MongoRepository<BhavCopy, String> {

    Optional<BhavCopy> findTopByTckrSymbOrderByTradDtDesc(String symbol);
    
    
    List<BhavCopy> findByTckrSymbIn(List<String> symbols);

}