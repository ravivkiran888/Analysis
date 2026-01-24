package com.analysis.runner;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.analysis.schedulers.VWAPScheduler;

@Component
public class VWAPRunner implements CommandLineRunner {

	@Value("${runvwap:false}")
	private boolean runVwap;

	private final VWAPScheduler vWapScheduler;

	
	public VWAPRunner(VWAPScheduler vWapScheduler) {

		this.vWapScheduler = vWapScheduler;

	}

	@Override
	public void run(String... args) throws Exception {
		if (runVwap) {
		//	vWapScheduler.runVWAPJob();
		}

		else {
			System.out.println("###########################");
			System.out.println("VWAP Disabled");
		}

	}
}
