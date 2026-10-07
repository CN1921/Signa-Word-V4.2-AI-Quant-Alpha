package com.keywordstock.model;

import java.math.BigDecimal;
import java.util.List;

public class KeywordStatsVO {
    private String keyword;
    private int total;
    private int up_count;
    private int down_count;
    private int flat_count;
    private BigDecimal up_ratio;
    private BigDecimal median_change;
    private BigDecimal max_up;
    private BigDecimal max_down;
    private int limit_up_count;
    private int limit_down_count;
    private List<String> matched_stocks;

    public KeywordStatsVO() {}

    // getters / setters
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public int getTotal() { return total; }
    public void setTotal(int total) { this.total = total; }
    public int getUp_count() { return up_count; }
    public void setUp_count(int up_count) { this.up_count = up_count; }
    public int getDown_count() { return down_count; }
    public void setDown_count(int down_count) { this.down_count = down_count; }
    public int getFlat_count() { return flat_count; }
    public void setFlat_count(int flat_count) { this.flat_count = flat_count; }
    public BigDecimal getUp_ratio() { return up_ratio; }
    public void setUp_ratio(BigDecimal up_ratio) { this.up_ratio = up_ratio; }
    public BigDecimal getMedian_change() { return median_change; }
    public void setMedian_change(BigDecimal median_change) { this.median_change = median_change; }
    public BigDecimal getMax_up() { return max_up; }
    public void setMax_up(BigDecimal max_up) { this.max_up = max_up; }
    public BigDecimal getMax_down() { return max_down; }
    public void setMax_down(BigDecimal max_down) { this.max_down = max_down; }
    public int getLimit_up_count() { return limit_up_count; }
    public void setLimit_up_count(int limit_up_count) { this.limit_up_count = limit_up_count; }
    public int getLimit_down_count() { return limit_down_count; }
    public void setLimit_down_count(int limit_down_count) { this.limit_down_count = limit_down_count; }
    public List<String> getMatched_stocks() { return matched_stocks; }
    public void setMatched_stocks(List<String> matched_stocks) { this.matched_stocks = matched_stocks; }
}
