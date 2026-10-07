package com.keywordstock.service;

import com.keywordstock.model.ConceptRankItem;
import com.keywordstock.model.StockDaily;
import com.keywordstock.util.AShareUniverse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ConceptService {
    private final DataLoader dataLoader;

    @Autowired
    public ConceptService(DataLoader dataLoader) { this.dataLoader = dataLoader; }

    public List<ConceptRankItem> topConcepts(double thresholdPct, int topN) {
        Map<String, StockDaily> all = dataLoader.getTodayStockMap();
        Map<String, List<String>> concepts = dataLoader.getStockConcepts();

        Map<String, Set<String>> conceptToStocks = new HashMap<>();
        for (Map.Entry<String, List<String>> e : concepts.entrySet()) {
            if (!AShareUniverse.isTarget(e.getKey())) continue;
            for (String concept : e.getValue()) {
                if (concept == null || concept.trim().isEmpty()) continue;
                conceptToStocks.computeIfAbsent(concept, k -> new LinkedHashSet<>()).add(e.getKey());
            }
        }

        int marketUp = (int) all.values().stream()
                .filter(s -> AShareUniverse.isTarget(s.getStockCode()) && s.getChangePct() != null && s.getChangePct().doubleValue() > thresholdPct)
                .count();

        List<ConceptRankItem> items = new ArrayList<>();
        for (Map.Entry<String, Set<String>> e : conceptToStocks.entrySet()) {
            List<StockDaily> stocks = e.getValue().stream()
                    .map(all::get).filter(Objects::nonNull).collect(Collectors.toList());
            if (stocks.isEmpty()) continue;

            int up = 0, down = 0, flat = 0, limitUp = 0, limitDown = 0;
            List<Double> changes = new ArrayList<>();
            for (StockDaily s : stocks) {
                double ch = s.getChangePct() == null ? 0 : s.getChangePct().doubleValue();
                changes.add(ch);
                if (ch > thresholdPct) up++; else if (ch < 0) down++; else flat++;
                if (isLimitUp(s, ch)) limitUp++;
                if (isLimitDown(s, ch)) limitDown++;
            }
            Collections.sort(changes);
            double median = changes.isEmpty() ? 0 : (changes.size() % 2 == 1
                    ? changes.get(changes.size()/2)
                    : (changes.get(changes.size()/2-1)+changes.get(changes.size()/2))/2.0);

            ConceptRankItem it = new ConceptRankItem();
            it.setConcept(e.getKey());
            it.setCount(up);
            // legacy fields now describe the concept itself; market-wide value remains in market_up_stocks
            it.setTotal_up_stocks(up);
            it.setRatio(stocks.size() == 0 ? 0 : up * 100.0 / stocks.size());
            it.setStocks(stocks.stream().map(StockDaily::getStockName).filter(Objects::nonNull).collect(Collectors.toList()));
            it.setTotal_stocks(stocks.size());
            it.setUp_stocks(up);
            it.setDown_stocks(down);
            it.setFlat_stocks(flat);
            it.setUp_ratio(stocks.size() == 0 ? 0 : up * 100.0 / stocks.size());
            it.setMedian_change(median);
            it.setLimit_up_count(limitUp);
            it.setLimit_down_count(limitDown);
            it.setMarket_up_stocks(marketUp);
            items.add(it);
        }

        items.sort(Comparator.comparing(ConceptRankItem::getUp_stocks, Comparator.reverseOrder())
                .thenComparing(ConceptRankItem::getUp_ratio, Comparator.reverseOrder())
                .thenComparing(ConceptRankItem::getMedian_change, Comparator.reverseOrder()));
        return items.size() > topN ? new ArrayList<>(items.subList(0, topN)) : items;
    }
    private boolean isLimitUp(StockDaily stock, double change) {
        return change >= limitThreshold(stock);
    }

    private boolean isLimitDown(StockDaily stock, double change) {
        return change <= -limitThreshold(stock);
    }

    private double limitThreshold(StockDaily stock) {
        String code = stock.getStockCode() == null ? "" : stock.getStockCode();
        String name = stock.getStockName() == null ? "" : stock.getStockName().toUpperCase(Locale.ROOT);
        if (name.startsWith("ST") || name.startsWith("*ST")) return 9.5;
        if (code.startsWith("300") || code.startsWith("688")) return 19.5;
        return 9.5;
    }

}
