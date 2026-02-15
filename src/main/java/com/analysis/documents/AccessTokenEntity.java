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
@Document(collection = Constants.ACCESS_TOKEN_COLLECTION)
public class AccessTokenEntity {
    @Id
    private String id;
    
    private String accessToken;
  
    private Date expiresAt;
    
    // This will create a constructor for non-final fields without @NonNull
    public AccessTokenEntity(String accessToken, Date expiresAt) {
        this.accessToken = accessToken;
        this.expiresAt = expiresAt;
    }
}