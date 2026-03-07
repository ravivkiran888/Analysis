package com.analysis.repository;

import com.analysis.documents.OptionSymbol;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OptionSymbolRepository extends MongoRepository<OptionSymbol, String> {
    // You can add custom query methods if needed
}