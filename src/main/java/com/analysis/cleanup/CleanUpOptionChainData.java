package com.analysis.cleanup;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.mongodb.client.result.DeleteResult;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class CleanUpOptionChainData {

    private final MongoTemplate mongoTemplate;

    public CleanUpOptionChainData(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Scheduled(cron = "0 1 9 ? * MON-FRI", zone = "Asia/Kolkata")
    public void deleteAllDocuments() {

        try {
            DeleteResult result =
                    mongoTemplate.remove(new Query(), "option_chain");

            log.info("Cleanup completed. Deleted {} documents.",
                    result.getDeletedCount());

        } catch (Exception ex) {
            log.error("Error during scheduled cleanup", ex);
        }
    }
}
