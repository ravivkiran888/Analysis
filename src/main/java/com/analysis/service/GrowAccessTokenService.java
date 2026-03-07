package com.analysis.service;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import com.analysis.documents.GrowAccessTokenEntity;

@Service
public class GrowAccessTokenService {

    private final MongoTemplate mongoTemplate;

    public GrowAccessTokenService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public String getGrowAccessToken() {
    	GrowAccessTokenEntity entity =
            mongoTemplate.findAll(GrowAccessTokenEntity.class)
                         .stream()
                         .findFirst()
                         .orElseThrow(() ->
                             new RuntimeException("Access token not found in MongoDB"));

        return entity.getAccessToken();
    }
}
