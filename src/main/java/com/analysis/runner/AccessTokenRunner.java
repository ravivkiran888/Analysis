package com.analysis.runner;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.analysis.APPConstants;
import com.analysis.documents.AccessToken;

@SpringBootApplication
public class AccessTokenRunner  implements CommandLineRunner{
	
	 @Value("${runaccesskeyinsertor:false}")
	  private boolean runaccesskeyinsertor;


    private final MongoTemplate mongoTemplate;

    public AccessTokenRunner(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

   

    @Override
    public void run(String... args) {

    	if(runaccesskeyinsertor)
    	{
    	
        Date expiresAt = Date.from(
                Instant.now().plus(24, ChronoUnit.HOURS)
        );

        
        String accessToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1bmlxdWVfbmFtZSI6IjUwNDA3ODI0Iiwicm9sZSI6IjIwMTkyIiwiU3RhdGUiOiIiLCJSZWRpcmVjdFNlcnZlciI6IkEiLCJuYmYiOjE3NzAzMzc3MjAsImV4cCI6MTc3MDQwMjU5OSwiaWF0IjoxNzcwMzM3NzIwfQ.piZSADctzy7fG1_sTnV7Saryi3XqbyDjtWREbdUO1Lc";
        
        AccessToken token = new AccessToken(
        		accessToken,
                expiresAt
        );

        mongoTemplate.save(token, APPConstants.ACCESS_TOKEN_COLLECTION);

        System.out.println("Access token inserted with 24-hour expiry");
        
    	}
        
    }
}
