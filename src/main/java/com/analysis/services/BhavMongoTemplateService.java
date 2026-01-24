package com.analysis.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import com.analysis.APPConstants;
import com.analysis.ScripMaster;
import com.analysis.dto.BhavCopy;
import com.analysis.dto.SymbolBhavDTO;

@Service
public class BhavMongoTemplateService {

    private final MongoTemplate mongoTemplate;

    public BhavMongoTemplateService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public List<SymbolBhavDTO> getBhavSummary() {

        // 1. Fetch scrip master
        List<ScripMaster> scrips =
                mongoTemplate.findAll(ScripMaster.class,
                        APPConstants.SCRIPT_SYMBOL_COLLECTION);

        // 2. Fetch bhavcopy
        List<BhavCopy> bhavs =
                mongoTemplate.findAll(BhavCopy.class,
                        "bhavcopy");

        // 3. Create lookup map from bhavcopy
        Map<String, BhavCopy> bhavMap =
                bhavs.stream()
                     .collect(Collectors.toMap(
                             BhavCopy::getTckrSymb,
                             b -> b,
                             (a, b) -> a
                     ));

        // 4. Join & build DTO
        List<SymbolBhavDTO> result = new ArrayList<>();

        for (ScripMaster scrip : scrips) {
            BhavCopy bhav = bhavMap.get(scrip.getSymbol());

            if (bhav != null) {
                result.add(new SymbolBhavDTO(
                        scrip.getSymbol(),
                        bhav.getOpnPric(),
                        bhav.getTtlTradgVol(),
                        bhav.getTtlTrfVal()
                ));
            }
        }

        return result;
    }
}
