package com.analysis.services;


import org.springframework.stereotype.Service;

import com.analysis.documents.AccessTokenEntity;
import com.analysis.repositories.AccessTokenRepository;

@Service
public class AccessTokenService {

    private final AccessTokenRepository repository;

    public AccessTokenService(AccessTokenRepository repository) {
        this.repository = repository;
    }

    public String getAccessToken() {
        return repository.findAll()
                         .stream()
                         .findFirst()
                         .map(AccessTokenEntity::getAccessToken)
                         .orElseThrow(() ->
                             new RuntimeException("Access token not found in MongoDB"));
    }
}
