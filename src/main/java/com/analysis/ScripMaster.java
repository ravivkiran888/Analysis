package com.analysis;

import org.springframework.data.mongodb.core.mapping.Document;

@Document(APPConstants.SCRIPT_SYMBOL_COLLECTION)
public class ScripMaster {

	private String ScripCode;
    private String Symbol;
	
    public String getScripCode() {
		return ScripCode;
	}
	public void setScripCode(String scripCode) {
		ScripCode = scripCode;
	}
	public String getSymbol() {
		return Symbol;
	}
	public void setSymbol(String symbol) {
		Symbol = symbol;
	}
	
}
