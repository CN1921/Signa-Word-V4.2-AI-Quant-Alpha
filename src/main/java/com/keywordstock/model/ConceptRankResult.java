package com.keywordstock.model;

import java.util.List;

public class ConceptRankResult {
    private List<ConceptRankItem> data;

    public ConceptRankResult() {}
    public ConceptRankResult(List<ConceptRankItem> data) { this.data = data; }

    public List<ConceptRankItem> getData() { return data; }
    public void setData(List<ConceptRankItem> data) { this.data = data; }
}
