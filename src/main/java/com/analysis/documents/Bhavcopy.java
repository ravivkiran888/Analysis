package com.analysis.documents;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import com.analysis.constants.Constants;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Document(collection = Constants.BHAVCOPY_COLLECTION)
public class Bhavcopy {
    @Id
    private String id;
    private LocalDate TradDt;
    private LocalDate BizDt;
    private String Sgmt;
    private String Src;
    private String FinInstrmTp;
    private Integer FinInstrmId;          // scripCode
    private String ISIN;
    private String TckrSymb;               // symbol (unique key)
    private String SctySrs;
    private String XpryDt;
    private String FininstrmActlXpryDt;
    private String StrkPric;
    private String OptnTp;
    private String FinInstrmNm;
    private BigDecimal OpnPric;
    private BigDecimal HghPric;             // previous day high
    private BigDecimal LwPric;
    private BigDecimal ClsPric;              // previous day close
    private BigDecimal LastPric;
    private BigDecimal PrvsClsgPric;
    private String UndrlygPric;
    private BigDecimal SttlmPric;
    private String OpnIntrst;
    private String ChngInOpnIntrst;
    private Long TtlTradgVol;                 // total volume
    private BigDecimal TtlTrfVal;
    private Integer TtlNbOfTxsExctd;
    private String SsnId;
    private Integer NewBrdLotQty;
    private String Rmks;
    private String Rsvd1;
    private String Rsvd2;
    private String Rsvd3;
    private String Rsvd4;
}