package com.analysis.runner;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.analysis.scanner.SymbolScanner;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class OrchestratorTestRunner implements CommandLineRunner {

	private final SymbolScanner orchestrator;

	@Value("${runscanner:false}")
	private boolean enabled;

	public OrchestratorTestRunner(SymbolScanner orchestrator) {
		this.orchestrator = orchestrator;
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
			orchestrator.scan();
		} catch (Exception e) {
			log.error("Manual orchestrator execution FAILED", e);
		}

		log.info("==== Orchestrator Test Finished in {} ms ====", System.currentTimeMillis() - start);
	}
}