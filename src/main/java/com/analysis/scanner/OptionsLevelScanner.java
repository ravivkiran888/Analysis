package com.analysis.scanner;



import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import com.analysis.apicalls.OptionsAPIClient;
import com.analysis.constants.Constants;
import com.analysis.documents.OptionSymbol;
import com.analysis.repository.OptionSymbolRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class OptionsLevelScanner {

	// ===================== CONSTANTS =====================
	private static final int THREAD_POOL_SIZE = 8;
	private static final int SCAN_TIMEOUT_MINUTES = 4;
	private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");

	private final OptionsAPIClient optionsApiClient;
	private final MongoTemplate mongoTemplate;
	private final ObjectMapper objectMapper;
	private final OptionSymbolRepository optionSymbolRepository;
	private final ExecutorService executor;
	private final AtomicBoolean isScanning = new AtomicBoolean(false);
	private volatile LocalTime lastScanStartTime = null;
	
	@Value("${EXPIRY_DATE:}")
	String expiryDate;

	public OptionsLevelScanner(MongoTemplate mongoTemplate, OptionsAPIClient optionsApiClient,
			OptionSymbolRepository optionSymbolRepository) {
		this.mongoTemplate = mongoTemplate;
		this.optionsApiClient = optionsApiClient;
		this.objectMapper = new ObjectMapper();
		this.optionSymbolRepository = optionSymbolRepository;
		this.executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
		log.info("Initialized OptionsLevelScanner with thread pool of {} threads", THREAD_POOL_SIZE);
	}

//	@Scheduled(cron = "0 */5 9-15 * * MON-FRI", zone = "Asia/Kolkata")
	public void scan() {
		LocalTime now = LocalTime.now(IST_ZONE);
		if (!isScanning.compareAndSet(false, true)) {
			log.warn("⚠️ Previous options scan still running at {}, skipping this execution", now);
			return;
		}
		lastScanStartTime = now;
		try {
			executeScan(now);
		} catch (Exception e) {
			log.error("Fatal error during options level scan", e);
		} finally {
			isScanning.set(false);
			log.info("Options level scan lock released at {}", LocalTime.now(IST_ZONE));
		}
	}

	private void executeScan(LocalTime startTime) {
		log.info("🔍 Starting options level scan at {} IST", startTime);
		List<OptionSymbol> allOptionSymbols = optionSymbolRepository.findAll();

		List<String> symbols = allOptionSymbols.stream().map(OptionSymbol::getSymbol).collect(Collectors.toList());
		
	

		CountDownLatch latch = new CountDownLatch(symbols.size());
		AtomicInteger processed = new AtomicInteger(0);
		AtomicInteger successful = new AtomicInteger(0);
		AtomicInteger failed = new AtomicInteger(0);

		for (String symbol : symbols) {
			if (shouldAbortScan(startTime)) {
				log.warn("⚠️ Aborting options scan at {} IST to prevent overlap", LocalTime.now(IST_ZONE));
				break;
			}
			executor.submit(() -> {
				try {
					Constants.RATE_LIMITER.acquire();
					processSymbol(symbol);
					successful.incrementAndGet();
				} catch (Exception e) {
					failed.incrementAndGet();
					log.error("Error processing {}: {}", symbol, e.getMessage());
				} finally {
					processed.incrementAndGet();
					latch.countDown();
				}
			});
		}

		try {
			boolean completed = latch.await(SCAN_TIMEOUT_MINUTES, TimeUnit.MINUTES);
			if (!completed) {
				log.warn("⚠️ Options scan timed out after {} minutes. Completed: {}/{}", SCAN_TIMEOUT_MINUTES,
						processed.get(), symbols.size());
			} else {
				log.info("✅ Options scan completed in {} ms at {} IST (Success: {}, Failed: {})",
						System.currentTimeMillis() - startTime.toNanoOfDay() / 1_000_000, LocalTime.now(IST_ZONE),
						successful.get(), failed.get());
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			log.error("Options scan was interrupted", e);
		}
	}

	private boolean shouldAbortScan(LocalTime startTime) {
		long elapsedSeconds = java.time.Duration.between(startTime, LocalTime.now(IST_ZONE)).getSeconds();
		return elapsedSeconds > 240; // 4 minutes
	}

	// -------------------- PER‑SYMBOL PROCESSING --------------------

	private void processSymbol(String symbol) {

	    try {
	        String response = optionsApiClient.getOptionsChain(symbol);

	        if (response == null || response.isBlank()) {
	            log.warn("Empty API response for {}", symbol);
	            return;
	        }

	        JsonNode root = objectMapper.readTree(response);

	        if (!root.has("payload")) {
	            throw new IllegalStateException("Missing payload");
	        }

	        JsonNode payload = root.get("payload");

	        if (!payload.has("underlying_ltp") || !payload.has("strikes")) {
	            throw new IllegalStateException("Invalid payload structure");
	        }

	        double ltp = payload.get("underlying_ltp").asDouble();
	        JsonNode strikes = payload.get("strikes");

	        double atmStrike = findATMStrike(strikes, ltp);

	        JsonNode atmNode = strikes.get(String.valueOf(atmStrike));
	        if (atmNode == null) {
	            atmNode = strikes.get(String.valueOf((int) atmStrike));
	        }

	        if (atmNode == null) {
	            throw new IllegalStateException("ATM strike not found");
	        }

	        JsonNode ce = atmNode.get("CE");
	        JsonNode pe = atmNode.get("PE");

	        if (ce == null || pe == null) {
	            throw new IllegalStateException("CE/PE missing");
	        }

	        // ✅ Current values
	        double ceOi = ce.path("open_interest").asDouble(0);
	        double peOi = pe.path("open_interest").asDouble(0);

	        double ceVol = ce.path("volume").asDouble(0);
	        double peVol = pe.path("volume").asDouble(0);

	        double ceIv = ce.path("greeks").path("iv").asDouble(0);
	        double peIv = pe.path("greeks").path("iv").asDouble(0);

	        // ✅ Fetch previous from SAME collection
	        Query query = new Query(Criteria.where("symbol").is(symbol)
	                .and("expiry").is(expiryDate));

	        Document prev = mongoTemplate.findOne(query, Document.class, "option_chain");

	        double prevCeOi = 0, prevPeOi = 0, prevLtp = 0, prevCeIv = 0;

	        if (prev != null) {
	            prevLtp = getDouble(prev, "ltp");

	            Document ceDoc = (Document) prev.get("ce");
	            Document peDoc = (Document) prev.get("pe");

	            if (ceDoc != null) {
	                prevCeOi = getDouble(ceDoc, "oi");
	                prevCeIv = getDouble(ceDoc, "iv");
	            }

	            if (peDoc != null) {
	                prevPeOi = getDouble(peDoc, "oi");
	            }
	        }

	        // ✅ Delta Calculations
	        double ceOiChange = ceOi - prevCeOi;
	        double peOiChange = peOi - prevPeOi;

	        // ✅ OI Build-up
	        String oiBuildUp;
	        if (peOiChange > 0 && ltp > prevLtp) oiBuildUp = "PUT_WRITING";
	        else if (ceOiChange > 0 && ltp < prevLtp) oiBuildUp = "CALL_WRITING";
	        else if (ceOiChange > 0 && ltp > prevLtp) oiBuildUp = "LONG_BUILDUP";
	        else if (peOiChange > 0 && ltp < prevLtp) oiBuildUp = "SHORT_BUILDUP";
	        else oiBuildUp = "NEUTRAL";

	        // ✅ Volume Spike
	        String volumeSpike;
	        if (peVol > ceVol * 1.5) volumeSpike = "PE";
	        else if (ceVol > peVol * 1.5) volumeSpike = "CE";
	        else volumeSpike = "BALANCED";

	        // ✅ IV Trend
	        String ivTrend;
	        if (ceIv > prevCeIv + 2) ivTrend = "RISING";
	        else if (ceIv < prevCeIv - 2) ivTrend = "FALLING";
	        else ivTrend = "STABLE";

	        // ✅ PCR
	        double pcr = (ceOi == 0) ? (peOi > 0 ? Double.MAX_VALUE : 0) : peOi / ceOi;

	        // ✅ Scoring Engine
	        int score = 50;

	        if (pcr > 1.2) score += 15;
	        else if (pcr < 0.8) score -= 15;

	        if ("PUT_WRITING".equals(oiBuildUp)) score += 20;
	        if ("CALL_WRITING".equals(oiBuildUp)) score -= 20;

	        if ("PE".equals(volumeSpike)) score += 10;
	        if ("CE".equals(volumeSpike)) score -= 10;

	        if ("RISING".equals(ivTrend)) score += 5;

	        score = Math.max(0, Math.min(100, score));

	        // ✅ Final Decision
	        String action;
	        String bias;

	        if (score >= 70) {
	            action = "BUY";
	            bias = "BULLISH";
	        } else if (score <= 30) {
	            action = "SELL";
	            bias = "BEARISH";
	        } else if (score >= 45 && score <= 55) {
	            action = "NO_TRADE";
	            bias = "NEUTRAL";
	        } else {
	            action = "WAIT";
	            bias = score > 50 ? "BULLISH" : "BEARISH";
	        }

	        // ✅ Update (INCLUDING previous values)
	        Update update = new Update()
	                .set("timestamp", Instant.now())
	                .set("ltp", ltp)
	                .set("prev_ltp", prevLtp)
	                .set("atm_strike", atmStrike)

	                .set("pcr", pcr)
	                .set("confidence", score)
	                .set("bias", bias)
	                .set("action", action)

	                // CE
	                .set("ce.oi", ceOi)
	                .set("ce.prev_oi", prevCeOi)
	                .set("ce.volume", ceVol)
	                .set("ce.iv", ceIv)

	                // PE
	                .set("pe.oi", peOi)
	                .set("pe.prev_oi", prevPeOi)
	                .set("pe.volume", peVol)
	                .set("pe.iv", peIv)

	                // Factors
	                .set("factors.oi_build_up", oiBuildUp)
	                .set("factors.volume_spike", volumeSpike)
	                .set("factors.iv_trend", ivTrend)

	                .setOnInsert("symbol", symbol)
	                .setOnInsert("expiry", expiryDate);

	        mongoTemplate.upsert(query, update, "option_chain");

	    } catch (Exception e) {
	        log.error("Error processing {}", symbol, e);
	    }
	}


	 private double findATMStrike(JsonNode strikesNode, double ltp) {
	        double closestStrike = 0;
	        double minDiff = Double.MAX_VALUE;

	        Iterator<Map.Entry<String, JsonNode>> fields = strikesNode.fields();

	        while (fields.hasNext()) {
	            Map.Entry<String, JsonNode> entry = fields.next();
	            double strike = Double.parseDouble(entry.getKey());

	            double diff = Math.abs(strike - ltp);
	            if (diff < minDiff) {
	                minDiff = diff;
	                closestStrike = strike;
	            }
	        }
	        return closestStrike;
	    }
	 
	 private double getDouble(Document doc, String key) {
		    Object val = doc.get(key);
		    return val instanceof Number ? ((Number) val).doubleValue() : 0.0;
		}
	
	// -------------------- CLEANUP --------------------
	@PreDestroy
	public void cleanup() {
		log.info("Shutting down OptionsLevelScanner thread pool...");
		executor.shutdown();
		try {
			if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
				executor.shutdownNow();
				if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
					log.error("OptionsLevelScanner thread pool did not terminate");
				}
			}
		} catch (InterruptedException e) {
			executor.shutdownNow();
			Thread.currentThread().interrupt();
		}
		log.info("OptionsLevelScanner thread pool shut down successfully");
	}
}