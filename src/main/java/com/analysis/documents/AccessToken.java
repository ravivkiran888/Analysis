package com.analysis.documents;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import com.analysis.APPConstants;

import java.util.Date;


@Document(collection = APPConstants.ACCESS_TOKEN_COLLECTION)
public class AccessToken {

    @Id
    private String id;
    private String accessToken;
    private Date expiresAt;

    public AccessToken(String accessToken, Date expiresAt) {
        this.accessToken = accessToken;
        this.expiresAt = expiresAt;
    }
}