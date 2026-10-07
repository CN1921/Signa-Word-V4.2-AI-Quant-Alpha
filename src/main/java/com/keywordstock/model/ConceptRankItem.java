package com.keywordstock.model;

import java.util.List;

public class ConceptRankItem {
    private String concept;
    private int count;
    private int total_up_stocks;
    private double ratio;
    private List<String> stocks;
    private int total_stocks;
    private int up_stocks;
    private int down_stocks;
    private int flat_stocks;
    private double up_ratio;
    private double median_change;
    private int limit_up_count;
    private int limit_down_count;
    private int market_up_stocks;

    public ConceptRankItem() {}

    // getters/setters
    public String getConcept() { return concept; }
    public void setConcept(String concept) { this.concept = concept; }
    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }
    public int getTotal_up_stocks() { return total_up_stocks; }
    public void setTotal_up_stocks(int total_up_stocks) { this.total_up_stocks = total_up_stocks; }
    public double getRatio() { return ratio; }
    public void setRatio(double ratio) { this.ratio = ratio; }
    public List<String> getStocks() { return stocks; }
    public void setStocks(List<String> stocks) { this.stocks = stocks; }
    public int getTotal_stocks() { return total_stocks; }
    public void setTotal_stocks(int total_stocks) { this.total_stocks = total_stocks; }
    public int getUp_stocks() { return up_stocks; }
    public void setUp_stocks(int up_stocks) { this.up_stocks = up_stocks; }
    public int getDown_stocks() { return down_stocks; }
    public void setDown_stocks(int down_stocks) { this.down_stocks = down_stocks; }
    public int getFlat_stocks() { return flat_stocks; }
    public void setFlat_stocks(int flat_stocks) { this.flat_stocks = flat_stocks; }
    public double getUp_ratio() { return up_ratio; }
    public void setUp_ratio(double up_ratio) { this.up_ratio = up_ratio; }
    public double getMedian_change() { return median_change; }
    public void setMedian_change(double median_change) { this.median_change = median_change; }
    public int getLimit_up_count() { return limit_up_count; }
    public void setLimit_up_count(int limit_up_count) { this.limit_up_count = limit_up_count; }
    public int getLimit_down_count() { return limit_down_count; }
    public void setLimit_down_count(int limit_down_count) { this.limit_down_count = limit_down_count; }
    public int getMarket_up_stocks() { return market_up_stocks; }
    public void setMarket_up_stocks(int market_up_stocks) { this.market_up_stocks = market_up_stocks; }
}
