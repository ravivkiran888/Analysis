package com.analysis.documents;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Document(collection = "ema_60m")
public class EMA60m {

    @Indexed
    private int scripCode;
    private String interval; // 
    @Indexed
    private LocalDateTime timestamp;
    private BigDecimal ema20;
    private BigDecimal ema50;


}
