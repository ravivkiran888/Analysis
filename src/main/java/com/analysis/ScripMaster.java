package com.analysis;

import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Getter;
import lombok.Setter;

@Document(APPConstants.SCRIPT_SYMBOL_COLLECTION)
@Setter
@Getter
public class ScripMaster {

	private String ScripCode;
    private String Symbol;
    private String Sector;
    
}
