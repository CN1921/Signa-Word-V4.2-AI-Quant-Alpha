package com.keywordstock.model;

public class StockConcept {
    private String stockCode;
    private String conceptName;

    public StockConcept() {}

    public StockConcept(String stockCode, String conceptName) {
        this.stockCode = stockCode;
        this.conceptName = conceptName;
    }

    public String getStockCode() { return stockCode; }
    public void setStockCode(String stockCode) { this.stockCode = stockCode; }
    public String getConceptName() { return conceptName; }
    public void setConceptName(String conceptName) { this.conceptName = conceptName; }
}
