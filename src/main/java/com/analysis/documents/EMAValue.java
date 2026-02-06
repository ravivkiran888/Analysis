package com.analysis.documents;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter

@Data
@Document(collection = "ema_5m")
public class EMAValue {

    @Id
    private String id;

    @Field("ScripCode")
    private String scripCode;
    private String Symbol;

    private BigDecimal ema20;
    private BigDecimal ema50;

    private BigDecimal avgVolume20;
    private BigDecimal lastVolume;

    private Boolean volumeExpansion;

    private String interval;
    private Instant lastCandleTime;
    private Instant updatedAt;
}
