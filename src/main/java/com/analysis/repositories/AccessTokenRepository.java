package com.analysis.repositories;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.analysis.documents.AccessTokenEntity;

public interface AccessTokenRepository
        extends MongoRepository<AccessTokenEntity, String> {
}