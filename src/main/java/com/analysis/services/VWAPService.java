package com.analysis.services;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.analysis.documents.VWAPValue;
import com.analysis.repository.VWAPRepository;

@Service
public class VWAPService {

    private final VWAPRepository repository;

    public VWAPService(VWAPRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void saveOrUpdate(
            int scripCode,
            String symbol,
            BigDecimal vwap) {

        VWAPValue entity =
                repository.findBySymbol(symbol)
                        .orElseGet(VWAPValue::new);

        entity.setScripCode(scripCode);
        entity.setSymbol(symbol);
        entity.setVwap(vwap);
        entity.setUpdatedAt(Instant.now());

        repository.save(entity);
    }
}
