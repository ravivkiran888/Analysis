package com.analysis.model;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Document(collection = "rsi_values")
public class RSIValue {


	@Id
    private String id;

    @Indexed
    @Field("ScripCode")
    private String scripCode;

    public String getScripCode() {
		return scripCode;
	}

	public void setScripCode(String scripCode) {
		this.scripCode = scripCode;
	}

	 @Field("Symbol")
	@Indexed
    private String symbol;

    private int period;

    private BigDecimal rsi;

    private Instant calculatedAt;

    public RSIValue() {}

    public RSIValue(String scripCode, String symbol, int period, BigDecimal rsi) {
        this.scripCode = scripCode;
        this.symbol = symbol;
        this.period = period;
        this.rsi = rsi;
        this.calculatedAt = Instant.now();
    }

    // getters and setters
}
