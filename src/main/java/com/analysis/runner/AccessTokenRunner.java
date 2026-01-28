package com.analysis.runner;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.core.MongoTemplate;

@SpringBootApplication
public class AccessTokenRunner  {

    private final MongoTemplate mongoTemplate;

    public AccessTokenRunner(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

   

    
    public void run(String... args) {

    	/*
    	
        Date expiresAt = Date.from(
                Instant.now().plus(24, ChronoUnit.HOURS)
        );

        
        String accessToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1bmlxdWVfbmFtZSI6IjUwNDA3ODI0Iiwicm9sZSI6IjIwMTkyIiwiU3RhdGUiOiIiLCJSZWRpcmVjdFNlcnZlciI6IkEiLCJuYmYiOjE3Njk1NTkwMjQsImV4cCI6MTc2OTYyNDk5OSwiaWF0IjoxNzY5NTU5MDI0fQ.3g4el6qSogSLpYPXvd5xkF5FFhDIYN4tjoa13GEl45w";
        
        AccessToken token = new AccessToken(
        		accessToken,
                expiresAt
        );

        mongoTemplate.save(token, APPConstants.ACCESS_TOKEN_COLLECTION);

        System.out.println("Access token inserted with 10-hour expiry");
        
        */
        
    }
}
