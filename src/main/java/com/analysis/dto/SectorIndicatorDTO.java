package com.analysis.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SectorIndicatorDTO {
    private String name;
    private String sector;
    private BigDecimal dayChange;
    private Instant timestamp;
}

