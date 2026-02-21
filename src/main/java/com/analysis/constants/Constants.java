package com.analysis.constants;

import com.google.common.util.concurrent.RateLimiter;

public class Constants {

	public static final String SYMBOL_INDICATORS_COLLECTION = "symbol_indicators";
	public static final String BHAVCOPY_COLLECTION = "bhavcopy";
	public static final String SCRIP_SYMBOL_EQ_COLLECTION = "scrip_symbol_eq";
	public static final String ACCESS_TOKEN_COLLECTION = "accessToken";
	
	public static final String GROW_ACCESS_TOKEN_COLLECTION = "growaccessToken";
	
	public static final String OPTION_SYMBOLS_COLLECTION = "option_symbols";
	public static final String SECTOR_INDICATORS_COLLECTION = "sector_indicators";
	
	public static final String SECTOR_INDICES_COLLECTION = "sector_indices";
	

	
	public static final String TCKRSYMB = "TckrSymb";
	public static final RateLimiter RATE_LIMITER = RateLimiter.create(8); // 18 requests/sec (safe under 20)
	
	public static final String BASE_URL = "https://openapi.5paisa.com";
	public static final String INTERVAL = "5m";
	
	public static final String ENTRY_READY = "ENTRY_READY";
	public static final String WAIT = "WAIT";
	public static final String EARLY = "EARLY";
	public static final String FULL = "FULL";
	public static final String WATCH = "WATCH";
	
	public static final String VOLUME_EXPANSION = "volumeExpansion";
	public static final String TOTAL_DAY_VOLUME = "totalDayVolume";
	public static final String SIGNAL = "signal";
	
	public static final String SCRIPT_CODE = "scripCode";

	 // MarketSnapshot API configuration
    public static final String MARKET_SNAPSHOT_URL = "/VendorsAPI/Service1.svc/v1/MarketSnapshot";
    public static final int MARKET_SNAPSHOT_BATCH_SIZE = 50;
    public static final String DEFAULT_CLIENT_CODE = "50407824"; // Make this configurable

}
