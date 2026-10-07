package com.keywordstock.model;

public class TrendPoint {
    private String date;
    private int count;
    private double ratio; // percent

    public TrendPoint() {}
    public TrendPoint(String date, int count, double ratio) {
        this.date = date;
        this.count = count;
        this.ratio = ratio;
    }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }
    public double getRatio() { return ratio; }
    public void setRatio(double ratio) { this.ratio = ratio; }
}
