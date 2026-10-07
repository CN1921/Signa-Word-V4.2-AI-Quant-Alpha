package com.keywordstock.model;

import java.math.BigDecimal;

public class StockDaily {
    private String stockCode;
    private String stockName;
    private BigDecimal open;
    private BigDecimal close;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal changePct; // percent, e.g., 2.5 means +2.5%
    private long volume;
    private long amount;

    public StockDaily() {}

    public StockDaily(String stockCode, String stockName, BigDecimal close, BigDecimal changePct) {
        this.stockCode = stockCode;
        this.stockName = stockName;
        this.close = close;
        this.changePct = changePct;
    }

    // getters/setters
    public String getStockCode() { return stockCode; }
    public void setStockCode(String stockCode) { this.stockCode = stockCode; }
    public String getStockName() { return stockName; }
    public void setStockName(String stockName) { this.stockName = stockName; }
    public BigDecimal getOpen() { return open; }
    public void setOpen(BigDecimal open) { this.open = open; }
    public BigDecimal getClose() { return close; }
    public void setClose(BigDecimal close) { this.close = close; }
    public BigDecimal getHigh() { return high; }
    public void setHigh(BigDecimal high) { this.high = high; }
    public BigDecimal getLow() { return low; }
    public void setLow(BigDecimal low) { this.low = low; }
    public BigDecimal getChangePct() { return changePct; }
    public void setChangePct(BigDecimal changePct) { this.changePct = changePct; }
    public long getVolume() { return volume; }
    public void setVolume(long volume) { this.volume = volume; }
    public long getAmount() { return amount; }
    public void setAmount(long amount) { this.amount = amount; }
}
