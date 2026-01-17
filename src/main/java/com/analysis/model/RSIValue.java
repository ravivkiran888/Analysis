package com.analysis.model;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "rsi_values")
public class RSIValue {

    public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public int getScripCode() {
		return scripCode;
	}

	public void setScripCode(int scripCode) {
		this.scripCode = scripCode;
	}

	public String getSymbol() {
		return symbol;
	}

	public void setSymbol(String symbol) {
		this.symbol = symbol;
	}

	public int getPeriod() {
		return period;
	}

	public void setPeriod(int period) {
		this.period = period;
	}

	public BigDecimal getRsi() {
		return rsi;
	}

	public void setRsi(BigDecimal rsi) {
		this.rsi = rsi;
	}

	public Instant getCalculatedAt() {
		return calculatedAt;
	}

	public void setCalculatedAt(Instant calculatedAt) {
		this.calculatedAt = calculatedAt;
	}

	@Id
    private String id;

    @Indexed
    private int scripCode;

    @Indexed
    private String symbol;

    private int period;

    private BigDecimal rsi;

    private Instant calculatedAt;

    public RSIValue() {}

    public RSIValue(int scripCode, String symbol, int period, BigDecimal rsi) {
        this.scripCode = scripCode;
        this.symbol = symbol;
        this.period = period;
        this.rsi = rsi;
        this.calculatedAt = Instant.now();
    }

    // getters and setters
}
