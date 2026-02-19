package com.analysis.runner;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.analysis.constants.Constants;
import com.analysis.documents.AccessTokenEntity;


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

        
        String accessToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1bmlxdWVfbmFtZSI6IjUwNDA3ODI0Iiwicm9sZSI6IjIwMTkyIiwiU3RhdGUiOiIiLCJSZWRpcmVjdFNlcnZlciI6IkEiLCJuYmYiOjE3NzE0NzMxODQsImV4cCI6MTc3MTUyNTc5OSwiaWF0IjoxNzcxNDczMTg0fQ.OmU4njET6oprDCs3bqu9z_c5uXJBxaHp8w3XQmK9QKk";
        
        AccessTokenEntity token = new AccessTokenEntity(
        		accessToken,
                expiresAt
        );

        mongoTemplate.save(token, Constants.ACCESS_TOKEN_COLLECTION);

        System.out.println("Access token inserted with 24-hour expiry");
        
    	}
        
    }
}