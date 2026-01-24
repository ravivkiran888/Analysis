package com.analysis.dto;


import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import lombok.Getter;
import lombok.Setter;


@Setter
@Getter
@Document("bhavcopy")
public class BhavCopy {
	
	
	   @Field("TradDt")
	    private String tradDt;

	    @Field("TckrSymb")
	    private String tckrSymb;

	    @Field("OpnPric")
	    private Double opnPric;

	    @Field("TtlTradgVol")
	    private Long ttlTradgVol;

	    @Field("TtlTrfVal")
	    private Double ttlTrfVal;
	    
	    @Field("ClsPric")
	    private Long clsPric;
	    
	    @Field("HghPric")
	    private Long hghPric;
	    
	    @Field("LwPric")
	    private Long lwPric;
}
