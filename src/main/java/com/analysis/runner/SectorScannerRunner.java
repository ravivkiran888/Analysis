package com.analysis.runner;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.analysis.scanner.SectorScanner;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SectorScannerRunner implements CommandLineRunner {

	private final SectorScanner sectorScanner;

	@Value("${runindices:false}")
	private boolean runindices;

	@Override
	public void run(String... args) throws Exception {
		

		if (runindices) {

			System.out.println("Starting Sector Scan...");
			
			sectorScanner.scan();
			
			System.out.println("Sector Scan Completed.");
		}
		else
		{
			System.out.println("Sector Scannar disabled");
		}
	
		
	}
}