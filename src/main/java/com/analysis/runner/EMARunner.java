package com.analysis.runner;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.analysis.schedulers.EMAScheduler;

@Component
public class EMARunner implements CommandLineRunner {

	
	 @Value("${runrsi:false}")
	  private boolean runrsi;
	
	
    private final EMAScheduler emaScheduler;
   
    public EMARunner(
            EMAScheduler emaScheduler
    ) {
        this.emaScheduler = emaScheduler;
    }

    @Override
	public void run(String... args) {

		if (runrsi) {
			System.out.println("$$$$$$$$$$$$$$$$$$$$$$$$$$$$");
			System.out.println("Start EMA ");

			try {
			//	emaScheduler.run();

			} catch (Exception e) {
				e.printStackTrace();
			}
			
			System.out.println("End EMA ");
		} 
		
		else {
			System.out.println("######################################");
			System.out.println("EMA disabled ");
		}

	
	}
}