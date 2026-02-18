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
                Instant.now().plus(12, ChronoUnit.HOURS)
        );

        
        String accessToken = "eyJraWQiOiJaTUtjVXciLCJhbGciOiJFUzI1NiJ9.eyJleHAiOjE3NzE0NjEwMDAsImlhdCI6MTc3MTQwNDY1NiwibmJmIjoxNzcxNDA0NjU2LCJzdWIiOiJ7XCJ0b2tlblJlZklkXCI6XCI1MGIxN2RlNi0wODgzLTQ4NmItOWRiMC0yYThhOTU5YmNhOTRcIixcInZlbmRvckludGVncmF0aW9uS2V5XCI6XCJlMzFmZjIzYjA4NmI0MDZjODg3NGIyZjZkODQ5NTMxM1wiLFwidXNlckFjY291bnRJZFwiOlwiNzEwMjIzNmUtNWY3Ny00YzA2LTg4YTktYTNmZjA3YjJlOTI1XCIsXCJkZXZpY2VJZFwiOlwiY2U5ZDUwYjUtYTdlMS01MDcyLWI2YzEtZjBmM2QwM2VkNmEwXCIsXCJzZXNzaW9uSWRcIjpcImQ3NzBlNDkzLTY4YjYtNDRiNi04YjViLTkwNmRmYzkxZTk5NVwiLFwiYWRkaXRpb25hbERhdGFcIjpcIno1NC9NZzltdjE2WXdmb0gvS0EwYkE3UHg0UEpSbFc0U0xUeHlxNU5EbVpSTkczdTlLa2pWZDNoWjU1ZStNZERhWXBOVi9UOUxIRmtQejFFQisybTdRPT1cIixcInJvbGVcIjpcIm9yZGVyLWJhc2ljLGxpdmVfZGF0YS1iYXNpYyxub25fdHJhZGluZy1iYXNpYyxvcmRlcl9yZWFkX29ubHktYmFzaWNcIixcInNvdXJjZUlwQWRkcmVzc1wiOlwiMjQwNToyMDE6YzQwYjoxMjQzOmYxMzA6NWM2Njo3M2QwOmM3MGIsMTcyLjY5LjE3OC4yNDYsMzUuMjQxLjIzLjEyM1wiLFwidHdvRmFFeHBpcnlUc1wiOjE3NzE0NjEwMDAwMDB9IiwiaXNzIjoiYXBleC1hdXRoLXByb2QtYXBwIn0.Ng-cMEDXoISlh_RjBoWMCR2l-C-lYLKVOmpuk4xaiB8XXxSkMRHTILDGtyWxtR208-vIxyBK-z_HxfCcyyFKzQ";
        
        GrowAccessTokenEntity token = new GrowAccessTokenEntity(
        		accessToken,
                expiresAt
        );

        mongoTemplate.save(token, Constants.GROW_ACCESS_TOKEN_COLLECTION);

        System.out.println("Access token inserted with 24-hour expiry");
        
    	}
        
    }
}