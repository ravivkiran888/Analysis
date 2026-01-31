package com.analysis.runner;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.analysis.services.impl.SignalScannerService;

@Component
public class SignalStateRunner implements CommandLineRunner {

	@Value("${runsignalscanner:false}")
	private boolean runsignalscanner;

	private final SignalScannerService signalScannerService;

	
	public SignalStateRunner(SignalScannerService signalScannerService) {

		this.signalScannerService = signalScannerService;

	}

	@Override
	public void run(String... args) throws Exception {
		if (runsignalscanner) {
			System.out.println("SignalScannerService start");
			signalScannerService.scanAll();
			System.out.println("SignalScannerService end");
		}

		

	}
}
