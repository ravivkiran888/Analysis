package com.analysis.runner;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.analysis.jobs.MarketSnapshotJob;
import com.analysis.scanner.SymbolScanner;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class OrchestratorTestRunner implements CommandLineRunner {

	private final SymbolScanner symbolScanner;
	
	private final MarketSnapshotJob marketSnapshotJob;

	@Value("${runindicatorscanner:false}")
	private boolean enabled;

	public OrchestratorTestRunner(SymbolScanner symbolScanner , MarketSnapshotJob marketSnapshotJob) {
		this.symbolScanner = symbolScanner;
		this.marketSnapshotJob = marketSnapshotJob;
	}

	@Override
	public void run(String... args) {

		if (!enabled) {
			log.info("Orchestrator test runner disabled by property");
			return;
		}

		log.info("==== Orchestrator Test Runner ENABLED ====");

		long start = System.currentTimeMillis();

		try {
			symbolScanner.scan();
			marketSnapshotJob.run();
			
		} catch (Exception e) {
			log.error("Manual orchestrator execution FAILED", e);
		}

		log.info("==== Orchestrator Test Finished in {} ms ====", System.currentTimeMillis() - start);
	}
}