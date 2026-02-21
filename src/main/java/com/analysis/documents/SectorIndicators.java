package com.analysis.documents;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import com.analysis.constants.Constants;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = Constants.SECTOR_INDICATORS_COLLECTION)
public class SectorIndicators {
    
    @Id
    private String id;
    private String sector;
    private String name;
    private Instant timestamp;
    private BigDecimal dayChange;
    private BigDecimal dayChangePercent;
}