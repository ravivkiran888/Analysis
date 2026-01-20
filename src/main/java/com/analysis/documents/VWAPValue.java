package com.analysis.documents;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import com.analysis.APPConstants;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Document(collection = APPConstants.VWAP_VALUES_COLLECTION)
public class VWAPValue {

    @Id
    private String id;

    
    @Field("ScripCode")
    private String scripCode;

    @Indexed
    @Field("Symbol")
    private String symbol;

    private BigDecimal vwap;

    private Instant updatedAt;
}
