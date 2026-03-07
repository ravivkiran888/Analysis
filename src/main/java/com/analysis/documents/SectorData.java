package com.analysis.documents;

import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import com.analysis.constants.Constants;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Document(collection = Constants.SECTOR_INDICATORS_COLLECTION)
public class SectorData {

	@Id
	private String id;
	private String sector;
	private String name;
	private Instant timestamp;
	private BigDecimal dayChange;
	private BigDecimal dayChangePercent;

	public SectorData() {
	}

	// Parameterized constructor
	public SectorData(String sector, String name, BigDecimal dayChange, BigDecimal dayChangePercent) {
		this.sector = sector;
		this.name = name;
		this.dayChange = dayChange;
		this.dayChangePercent = dayChangePercent;
		this.timestamp = Instant.now();
	}

}