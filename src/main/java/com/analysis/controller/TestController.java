package com.analysis.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;



@RestController
public class TestController {

	
	 @GetMapping("/sai")
	    public String sai() {
	        return "Hello, Sai!";
	    }
	 
	  @GetMapping("/status")
	    public String status() {
	        return "Service is UP";
	    }

	
}
