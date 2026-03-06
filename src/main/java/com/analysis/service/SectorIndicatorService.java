package com.analysis.service;

import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.analysis.constants.Constants;
import com.analysis.documents.SectorIndicators;
import com.analysis.dto.SectorIndicatorDTO;

@Service
public class SectorIndicatorService {

    private final MongoTemplate mongoTemplate;

    public SectorIndicatorService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }


    public List<SectorIndicatorDTO> getTopSectorsByDayChange() {
        List<SectorIndicators> sectors = mongoTemplate.findAll(SectorIndicators.class, Constants.SECTOR_INDICATORS_COLLECTION);

        return sectors.stream()
                .filter(s -> s.getDayChange() != null)               // <-- add filter here
                .filter(s -> StringUtils.hasText(s.getName()))
                .filter(s -> StringUtils.hasText(s.getSector()))
                .sorted(Comparator.comparing(SectorIndicators::getDayChange).reversed())
                .map(s -> new SectorIndicatorDTO(
                        s.getName(),
                        s.getSector(),
                        s.getDayChange().setScale(2, RoundingMode.HALF_UP),
                        s.getTimestamp()
                ))
                .collect(Collectors.toList());
    }
 
}