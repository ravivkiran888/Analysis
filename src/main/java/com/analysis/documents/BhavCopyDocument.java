package com.analysis.documents;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "bhavcopy")
public class BhavCopyDocument {
    @Id
    private String id;
    private String TradDt;
    private String TckrSymb;
    private double HghPric;
    private double LwPric;
    private double ClsPric;
    
    // Getters and Setters
    public String getTradDt() { return TradDt; }
    public void setTradDt(String tradDt) { TradDt = tradDt; }
    
    public String getTckrSymb() { return TckrSymb; }
    public void setTckrSymb(String tckrSymb) { TckrSymb = tckrSymb; }
    
    public double getHghPric() { return HghPric; }
    public void setHghPric(double hghPric) { HghPric = hghPric; }
    
    public double getLwPric() { return LwPric; }
    public void setLwPric(double lwPric) { LwPric = lwPric; }
    
    public double getClsPric() { return ClsPric; }
    public void setClsPric(double clsPric) { ClsPric = clsPric; }
}