package com.analysis.requests;

public class VWAPRequest {

    private final int scripCode;
    private final String url;

    public VWAPRequest(int scripCode, String url) {
        this.scripCode = scripCode;
        this.url = url;
    }

    public int getScripCode() {
        return scripCode;
    }

    public String getUrl() {
        return url;
    }
}