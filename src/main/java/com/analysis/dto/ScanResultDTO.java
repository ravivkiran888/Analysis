package com.analysis.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class ScanResultDTO {

    @JsonIgnore
    private String scripCode;
    private String symbol;
    private String close;
    private String volume;

    public ScanResultDTO() {}

    public ScanResultDTO(String scripCode, String symbol, String close, String volume) {
        this.scripCode = scripCode;
        this.symbol = symbol;
        this.close = close;
        this.volume = volume;
    }

}
