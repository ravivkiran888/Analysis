package com.analysis.runner;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.analysis.constants.Constants;
import com.analysis.documents.GrowAccessTokenEntity;


@SpringBootApplication
public class GrowAccessTokenRunner  implements CommandLineRunner{
	
	 @Value("${rungrowaccesskeyinsertor:false}")
	  private boolean rungrowaccesskeyinsertor;


    private final MongoTemplate mongoTemplate;

    public GrowAccessTokenRunner(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

   

    @Override
    public void run(String... args) {

    	if(rungrowaccesskeyinsertor)
    	{
    	
        Date expiresAt = Date.from(
                Instant.now().plus(24, ChronoUnit.HOURS)
        );

        
        String accessToken = "eyJraWQiOiJaTUtjVXciLCJhbGciOiJFUzI1NiJ9.eyJleHAiOjE3NzE1NDc0MDAsImlhdCI6MTc3MTQ2MzcyMSwibmJmIjoxNzcxNDYzNzIxLCJzdWIiOiJ7XCJ0b2tlblJlZklkXCI6XCJmODVlMTYzMS04OTM1LTQ4NzctOTJjMy03ZDU2YmMzMjZhYzRcIixcInZlbmRvckludGVncmF0aW9uS2V5XCI6XCJlMzFmZjIzYjA4NmI0MDZjODg3NGIyZjZkODQ5NTMxM1wiLFwidXNlckFjY291bnRJZFwiOlwiNzEwMjIzNmUtNWY3Ny00YzA2LTg4YTktYTNmZjA3YjJlOTI1XCIsXCJkZXZpY2VJZFwiOlwiY2U5ZDUwYjUtYTdlMS01MDcyLWI2YzEtZjBmM2QwM2VkNmEwXCIsXCJzZXNzaW9uSWRcIjpcIjY4ODQyMmQ2LTFjNDMtNDBiOS05N2NhLTYyMTEwMjllNzA1M1wiLFwiYWRkaXRpb25hbERhdGFcIjpcIno1NC9NZzltdjE2WXdmb0gvS0EwYkE3UHg0UEpSbFc0U0xUeHlxNU5EbVpSTkczdTlLa2pWZDNoWjU1ZStNZERhWXBOVi9UOUxIRmtQejFFQisybTdRPT1cIixcInJvbGVcIjpcIm9yZGVyLWJhc2ljLGxpdmVfZGF0YS1iYXNpYyxub25fdHJhZGluZy1iYXNpYyxvcmRlcl9yZWFkX29ubHktYmFzaWNcIixcInNvdXJjZUlwQWRkcmVzc1wiOlwiMjQwNToyMDE6YzQwYjoxMjQzOjQ0YzI6Njc1YjplZTJmOjdjNjksMTcyLjY5Ljg3LjU3LDM1LjI0MS4yMy4xMjNcIixcInR3b0ZhRXhwaXJ5VHNcIjoxNzcxNTQ3NDAwMDAwfSIsImlzcyI6ImFwZXgtYXV0aC1wcm9kLWFwcCJ9.WzY4xoNbI6pUX8Q-qsxOCOGWJr3fxkPLpdl9HSm99DCtZC6EEfTMLcDdSaWTcXN2VcLoi7vXToQo4xCjtv85ZQ";
        
        GrowAccessTokenEntity token = new GrowAccessTokenEntity(
        		accessToken,
                expiresAt
        );

        mongoTemplate.save(token, Constants.GROW_ACCESS_TOKEN_COLLECTION);

        System.out.println("Grow token inserted with 24-hour expiry");
        
    	}
        
    }
}