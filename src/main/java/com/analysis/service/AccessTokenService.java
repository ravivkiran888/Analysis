package com.analysis.service;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import com.analysis.documents.AccessTokenEntity;

@Service
public class AccessTokenService {

    private final MongoTemplate mongoTemplate;

    public AccessTokenService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public String getAccessToken() {
        AccessTokenEntity entity =
            mongoTemplate.findAll(AccessTokenEntity.class)
                         .stream()
                         .findFirst()
                         .orElseThrow(() ->
                             new RuntimeException("Access token not found in MongoDB"));

        return entity.getAccessToken();
    }
}
