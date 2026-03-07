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
public class CleanupBhavData {

    private final MongoTemplate mongoTemplate;

    public CleanupBhavData(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    // To run the job daily at 5:00 PM IST
    @Scheduled(cron = "0 0 17 * * ?", zone = "Asia/Kolkata")
    public void deleteAllDocuments() {

        try {
            DeleteResult result =
                    mongoTemplate.remove(new Query(), Constants.BHAVCOPY_COLLECTION);

            log.info("Cleanup bhavcopy completed. Deleted {} documents.",
                    result.getDeletedCount());

        } catch (Exception ex) {
            log.error("Error during scheduled cleanup", ex);
        }
    }
}
