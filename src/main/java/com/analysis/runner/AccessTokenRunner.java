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

        
        String accessToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1bmlxdWVfbmFtZSI6IjUwNDA3ODI0Iiwicm9sZSI6IjIwMTkyIiwiU3RhdGUiOiIiLCJSZWRpcmVjdFNlcnZlciI6IkEiLCJuYmYiOjE3NzAyMTc2MzksImV4cCI6MTc3MDIyOTc5OSwiaWF0IjoxNzcwMjE3NjM5fQ.wc_QzzYdsdLjdy7O9N_S-Zal2kWVbOLWDX-LRXOeUSY";
        
        AccessToken token = new AccessToken(
        		accessToken,
                expiresAt
        );

        mongoTemplate.save(token, APPConstants.ACCESS_TOKEN_COLLECTION);

        System.out.println("Access token inserted with 10-hour expiry");
        
    	}
        
    }
}
