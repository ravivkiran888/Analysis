package com.analysis.schedulers;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.analysis.helpers.VWAPApiBuilder;
import com.analysis.requests.VWAPRequest;
import com.analysis.services.ScripCache;
import com.analysis.util.VWAPAPIExecutor;

import jakarta.annotation.PostConstruct;

@Component
public class VWAPScheduler {


	private final ScripCache scripCache;
	 private final VWAPAPIExecutor vwapaAPIExecutor;

	
	
	
	 public VWAPScheduler(
	            ScripCache scripCache,
	            VWAPAPIExecutor vwapaAPIExecutor) {
	        this.scripCache = scripCache;
	        this.vwapaAPIExecutor = vwapaAPIExecutor;
	        
	        vwapScheduler() ;
	    }
	
	  //  @Scheduled(cron = "0 */3 * * * *")

	/*
	 @SchedulerLock(
	    name = APPConstants.VWAP_SCHEDULER,
	    lockAtMostFor = APPConstants.LOCK_FOR_IN_MINS
	)
	*/
	 
	 @PostConstruct
	    public void init() {
	        vwapScheduler();
	    }

	 
	public void vwapScheduler() {
	    runMarketJob();
	}

	
	    private void runMarketJob() {

	        List<Integer> scripCodes =
	                new ArrayList<>(scripCache.getAllScripCodes());

	        List<VWAPRequest> requests =
	                VWAPApiBuilder.buildRequests(scripCodes);

	        vwapaAPIExecutor.executeInBatches(requests, 50);
	    }




	    
}
