package com.analysis.dto;


import org.springframework.data.mongodb.core.mapping.Document;

@Document("bhavcopy")
public class BhavCopy {

    private String TradDt;
    private String TckrSymb;
    private Double OpnPric;
    private Long TtlTradgVol;
    private Double TtlTrfVal;

    public String getTradDt() {
        return TradDt;
    }

    public String getTckrSymb() {
        return TckrSymb;
    }

    public Double getOpnPric() {
        return OpnPric;
    }

    public Long getTtlTradgVol() {
        return TtlTradgVol;
    }

    public Double getTtlTrfVal() {
        return TtlTrfVal;
    }
}
