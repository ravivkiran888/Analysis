package com.analysis.documents;


import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "accessToken")
public class AccessTokenEntity {

    @Id
    private String id;

    private String accessToken;

    public String getAccessToken() {
        return accessToken;
    }
}
