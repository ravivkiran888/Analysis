package com.analysis.runner;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.analysis.APPConstants;
import com.analysis.documents.AccessToken;

@SpringBootApplication
public class AccessTokenInsertor {

    private final MongoTemplate mongoTemplate;

    public AccessTokenInsertor(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

   

  //  @Override
    public void runAccessTokenInsertor(String... args) {

        Date expiresAt = Date.from(
                Instant.now().plus(10, ChronoUnit.HOURS)
        );

        
        String accessToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1bmlxdWVfbmFtZSI6IjUwNDA3ODI0Iiwicm9sZSI6IjIwMTkyIiwiU3RhdGUiOiIiLCJSZWRpcmVjdFNlcnZlciI6IkEiLCJuYmYiOjE3NjkzNDYzNTAsImV4cCI6MTc2OTM2NTc5OSwiaWF0IjoxNzY5MzQ2MzUwfQ.A1Wp2n4jSmW6Z9LN3qDkzmX_rChNVbsjuL3-V4n5PQA";
        
        AccessToken token = new AccessToken(
        		accessToken,
                expiresAt
        );

      //  mongoTemplate.save(token, APPConstants.ACCESS_TOKEN_COLLECTION);

       // System.out.println("Access token inserted with 10-hour expiry");
    }
}
