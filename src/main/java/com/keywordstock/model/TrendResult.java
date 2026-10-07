package com.keywordstock.model;

import java.util.List;

public class TrendResult {
    private String keyword;
    private List<TrendPoint> points;

    public TrendResult() {}
    public TrendResult(String keyword, List<TrendPoint> points) { this.keyword = keyword; this.points = points; }

    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public List<TrendPoint> getPoints() { return points; }
    public void setPoints(List<TrendPoint> points) { this.points = points; }
}
