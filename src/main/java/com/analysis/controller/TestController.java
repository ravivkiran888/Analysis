package com.analysis.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;



@RestController
public class TestController {

	
		@Value("${EXPIRY_DATE:}")
	    private String expiryDate;
	
	 @GetMapping("/sai")
	    public String sai() {
	        return "Hello, Sai!";
	    }
	 
	  @GetMapping("/status")
	    public String status() {
	        return "Service is UP";
	    }

	  @GetMapping("/expiry")
	    public String getExpiryDate() {
	        return "Current expiry date: " + expiryDate;
	    }
	
}
