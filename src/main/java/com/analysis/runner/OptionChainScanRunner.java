package com.analysis.runner;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.analysis.scanner.OptionsLevelScanner;


@SpringBootApplication
public class OptionChainScanRunner  implements CommandLineRunner{
	
	 @Value("${runoptionchainscanner:false}")
	  private boolean runoptionchainscanner;


    private final OptionsLevelScanner optionsLevelScanner;

   public OptionChainScanRunner(OptionsLevelScanner optionsLevelScanner)
   {
	   this.optionsLevelScanner = optionsLevelScanner;
   }
   

    @Override
    public void run(String... args) {

    	if(runoptionchainscanner)
    	{
    		
    		optionsLevelScanner.scan();
    	}
        
    }
}