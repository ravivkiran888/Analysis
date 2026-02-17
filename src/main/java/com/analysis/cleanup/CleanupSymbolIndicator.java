package com.analysis.cleanup;

import com.analysis.constants.Constants;
import com.mongodb.client.result.DeleteResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class CleanupSymbolIndicator {

    private final MongoTemplate mongoTemplate;

    public CleanupSymbolIndicator(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    // To run the job daily at 8:00 AM IST
    @Scheduled(cron = "0 0 8 * * ?", zone = "Asia/Kolkata")
    public void deleteAllDocuments() {

        try {
            DeleteResult result =
                    mongoTemplate.remove(new Query(), Constants.SYMBOL_INDICATORS_COLLECTION);

            log.info("Cleanup completed. Deleted {} documents.",
                    result.getDeletedCount());

        } catch (Exception ex) {
            log.error("Error during scheduled cleanup", ex);
        }
    }
}
