package com.analysis.services;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.analysis.dto.ScripInfo;
import com.analysis.repository.ScripMasterRepository;

@Service
public class ScripCache {

    private final Map<Integer, ScripInfo> scripMap = new ConcurrentHashMap<>();

    public ScripCache(ScripMasterRepository repo) {
        repo.findAll().forEach(s ->
            scripMap.put(
                Integer.valueOf(s.getScripCode()),
                new ScripInfo(s.getSymbol(), s.getSector())
            )
        );
    }

    public Set<Integer> getAllScripCodes() {
        return scripMap.keySet();
    }
    
    public ScripInfo getScripInfo(int scripCode) {
        return scripMap.get(scripCode);
    }

    public Set<Map.Entry<Integer, ScripInfo>> getAllScripEntries() {
        return scripMap.entrySet();
    }
}
