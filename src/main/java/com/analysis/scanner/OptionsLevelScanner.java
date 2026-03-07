package com.analysis.scanner;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.analysis.apicalls.OptionsAPIClient;
import com.analysis.constants.Constants;
import com.analysis.documents.OptionSymbol;
import com.analysis.dto.OptionChainIndicators;
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

	public OptionsLevelScanner(MongoTemplate mongoTemplate, OptionsAPIClient optionsApiClient,
			OptionSymbolRepository optionSymbolRepository) {
		this.mongoTemplate = mongoTemplate;
		this.optionsApiClient = optionsApiClient;
		this.objectMapper = new ObjectMapper();
		this.optionSymbolRepository = optionSymbolRepository;
		this.executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
		log.info("Initialized OptionsLevelScanner with thread pool of {} threads", THREAD_POOL_SIZE);
	}

	@Scheduled(cron = "0 */15 9-15 * * MON-FRI", zone = "Asia/Kolkata")
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

	private void processSymbol(String symbol) throws Exception {

		String optionChainResponse = optionsApiClient.getOptionsChain(symbol);

		if (optionChainResponse == null)
			return;

		JsonNode root = objectMapper.readTree(optionChainResponse);
		JsonNode payload = root.path("payload");

		double underlying = payload.path("underlying_ltp").asDouble();
		JsonNode strikes = payload.path("strikes");

		int atmStrike = -1;
		double minDiff = Double.MAX_VALUE;

		int support = -1;
		int resistance = -1;

		int maxPutOI = 0;
		int maxCallOI = 0;

		int atmCallOI = 0;
		int atmPutOI = 0;

		int atmCallVolume = 0;
		int atmPutVolume = 0;

		Iterator<String> strikeIterator = strikes.fieldNames();

		while (strikeIterator.hasNext()) {

			String strikeKey = strikeIterator.next();
			int strike = Integer.parseInt(strikeKey);

			JsonNode strikeNode = strikes.get(strikeKey);

			int callOI = strikeNode.path("CE").path("open_interest").asInt();
			int putOI = strikeNode.path("PE").path("open_interest").asInt();

			int callVol = strikeNode.path("CE").path("volume").asInt();
			int putVol = strikeNode.path("PE").path("volume").asInt();

			// ATM calculation
			double diff = Math.abs(strike - underlying);
			if (diff < minDiff) {
				minDiff = diff;
				atmStrike = strike;

				atmCallOI = callOI;
				atmPutOI = putOI;

				atmCallVolume = callVol;
				atmPutVolume = putVol;
			}

			// Support (highest Put OI)
			if (putOI > maxPutOI) {
				maxPutOI = putOI;
				support = strike;
			}

			// Resistance (highest Call OI)
			if (callOI > maxCallOI) {
				maxCallOI = callOI;
				resistance = strike;
			}
		}

		int totalVolume = atmCallVolume + atmPutVolume;

		OptionChainIndicators indicators = OptionChainIndicators.builder().symbol(symbol).underlying(underlying)
				.atmStrike(atmStrike).support(support).resistance(resistance).atmCallOI(atmCallOI).atmPutOI(atmPutOI)
				.atmCallVolume(atmCallVolume).atmPutVolume(atmPutVolume).atmTotalVolume(totalVolume)
				.updatedAt(Instant.now()).build();

		saveIndicators(indicators);
	}

	private void saveIndicators(OptionChainIndicators indicators) {

		Query query = new Query(Criteria.where("symbol").is(indicators.getSymbol()));

		Update update = new Update().set("symbol", indicators.getSymbol()).set("underlying", indicators.getUnderlying())
				.set("atmStrike", indicators.getAtmStrike()).set("support", indicators.getSupport())
				.set("resistance", indicators.getResistance()).set("atmCallOI", indicators.getAtmCallOI())
				.set("atmPutOI", indicators.getAtmPutOI()).set("atmCallVolume", indicators.getAtmCallVolume())
				.set("atmPutVolume", indicators.getAtmPutVolume()).set("atmTotalVolume", indicators.getAtmTotalVolume())
				.set("updatedAt", indicators.getUpdatedAt());

		mongoTemplate.upsert(query, update, "optionChainIndicators");
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