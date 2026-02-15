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

        
        String accessToken = "";
        
        AccessTokenEntity token = new AccessTokenEntity(
        		accessToken,
                expiresAt
        );

        mongoTemplate.save(token, Constants.ACCESS_TOKEN_COLLECTION);

        System.out.println("Access token inserted with 24-hour expiry");
        
    	}
        
    }
}