package com.analysis.services;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.analysis.repository.ScripMasterRepository;

@Service
public class ScripCache {

    private final Map<Integer, String> scripMap = new ConcurrentHashMap<>();

    public ScripCache(ScripMasterRepository repo) {
        repo.findAll().forEach(
            s -> scripMap.put(
                Integer.valueOf(s.getScripCode()),
                s.getSymbol()
            )
        );
    }

    public String getSymbol(int scripCode) {
        return scripMap.get(scripCode);
    }

    public Set<Integer> getAllScripCodes() {
        return scripMap.keySet();
    }

    public Set<Map.Entry<Integer, String>> getAllScripEntries() {
        return scripMap.entrySet();
    }
}
