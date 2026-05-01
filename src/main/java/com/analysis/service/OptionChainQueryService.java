package com.analysis.service;

import java.util.List;

import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

@Service
public class OptionChainQueryService {

    @Autowired
    private MongoTemplate mongoTemplate;

    
    public List<Document> getAllOptionSymbols() {
        return mongoTemplate.findAll(Document.class, "option_chain");
    }
}