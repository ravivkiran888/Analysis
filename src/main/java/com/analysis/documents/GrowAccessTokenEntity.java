package com.analysis.documents;

import java.util.Date;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import com.analysis.constants.Constants;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = Constants.GROW_ACCESS_TOKEN_COLLECTION)
public class GrowAccessTokenEntity {
    @Id
    private String id;
    
    private String accessToken;
  
    private Date expiresAt;
    
    // This will create a constructor for non-final fields without @NonNull
    public GrowAccessTokenEntity(String accessToken, Date expiresAt) {
        this.accessToken = accessToken;
        this.expiresAt = expiresAt;
    }
}