package com.analysis;

import com.google.common.util.concurrent.RateLimiter;

public class APPConstants {

	public static final String DB_NAME = "test";
	
	public static final String LOCK_FOR_IN_MINS = "35m";
	

	public static final String VWAP_SCHEDULER = "VWAPSCheduler";

	public static final String DATE_FORMAT_YYYY = "yyyy-MM-dd";
	
	public static final String BASE_URL = "https://openapi.5paisa.com";
	
	public static final String EXCH = "N";
	public static final String EXCH_TYPE = "C";
	public static final String INTERVAL = "5m";

	public static final String SCRIPT_SYMBOL_COLLECTION = "scrip_symbol_eq";

	public static final String VWAP_VALUES_COLLECTION = "vwap_values";

	public static final String RSI_VALUES_COLLECTION = "rsi_values";

	public static final String SCRIPT_CODE = "ScripCode";
	public static final String SYMBOL = "Symbol";

	public static final RateLimiter RATE_LIMITER =
	        RateLimiter.create(0.33); // 1 request per 3 seconds


}
