package com.analysis.documents;

import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import com.analysis.constants.Constants;

import lombok.Getter;
import lombok.Setter;

@Document(collection = Constants.SCRIP_SYMBOL_EQ_COLLECTION)
@Setter
@Getter
public class ScripMaster {

	@Field("ScripCode")  
	private String ScripCode;
    private String Symbol;
    private String Sector;
    
}