package com.keywordstock.service;

import com.keywordstock.model.KeywordStatsVO;
import com.keywordstock.model.StockDaily;
import com.keywordstock.util.AShareUniverse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * KeywordService: core matching and aggregation logic
 */
@Service
public class KeywordService {

    private final DataLoader dataLoader;

    @Autowired
    public KeywordService(DataLoader dataLoader) {
        this.dataLoader = dataLoader;
    }

    /**
     * Compute keyword stats for concept keywords (matching concepts) or name keywords (matching stock name).
     * scope: "concept" | "name" | "both"
     */
    public List<KeywordStatsVO> computeKeywordStats(List<String> keywords, String scope) {
        if (keywords == null || keywords.isEmpty()) return Collections.emptyList();

        List<KeywordStatsVO> result = new ArrayList<>();
        Map<String, StockDaily> allStocks = dataLoader.getTodayStockMap();
        Map<String, List<String>> concepts = dataLoader.getStockConcepts();

        for (String rawKw : keywords) {
            String kw = rawKw.trim();
            if (kw.isEmpty()) continue;
            Set<String> matchedCodes = new LinkedHashSet<>();

            if ("concept".equalsIgnoreCase(scope) || "both".equalsIgnoreCase(scope)) {
                // match concept names containing keyword
                for (Map.Entry<String, List<String>> e : concepts.entrySet()) {
                    if (!AShareUniverse.isTarget(e.getKey())) continue;
                    for (String c : e.getValue()) {
                        if (c != null && c.contains(kw)) {
                            matchedCodes.add(e.getKey());
                            break;
                        }
                    }
                }
            }

            if ("name".equalsIgnoreCase(scope) || "both".equalsIgnoreCase(scope)) {
                for (StockDaily sd : allStocks.values()) {
                    if (!AShareUniverse.isTarget(sd.getStockCode())) continue;
                    if (sd.getStockName() != null && sd.getStockName().contains(kw)) {
                        matchedCodes.add(sd.getStockCode());
                    }
                }
            }

            KeywordStatsVO vo = buildStatsForMatched(kw, matchedCodes, allStocks);
            result.add(vo);
        }

        // default sort by up_ratio desc then median_change
        result.sort(Comparator.comparing(KeywordStatsVO::getUp_ratio, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(KeywordStatsVO::getMedian_change, Comparator.nullsLast(Comparator.reverseOrder())));
        return result;
    }

    private KeywordStatsVO buildStatsForMatched(String kw, Set<String> matchedCodes, Map<String, StockDaily> allStocks) {
        KeywordStatsVO vo = new KeywordStatsVO();
        vo.setKeyword(kw);
        vo.setTotal(matchedCodes.size());
        if (matchedCodes.isEmpty()) {
            vo.setUp_count(0);
            vo.setDown_count(0);
            vo.setFlat_count(0);
            vo.setUp_ratio(BigDecimal.ZERO);
            vo.setMedian_change(BigDecimal.ZERO);
            vo.setMax_up(BigDecimal.ZERO);
            vo.setMax_down(BigDecimal.ZERO);
            vo.setLimit_up_count(0);
            vo.setLimit_down_count(0);
            vo.setMatched_stocks(Collections.emptyList());
            return vo;
        }

        List<Double> changes = new ArrayList<>();
        int up = 0, down = 0, flat = 0, limitUp = 0, limitDown = 0;
        List<String> matchedNames = new ArrayList<>();

        for (String code : matchedCodes) {
            StockDaily sd = allStocks.get(code);
            if (sd == null) continue;
            double change = sd.getChangePct() != null ? sd.getChangePct().doubleValue() : 0.0;
            changes.add(change);
            matchedNames.add(sd.getStockName());
            if (change > 0) up++;
            else if (change < 0) down++;
            else flat++;

            if (isLimitUp(sd, change)) limitUp++;
            if (isLimitDown(sd, change)) limitDown++;
        }

        Collections.sort(changes);
        double median = computeMedian(changes);
        double maxUp = changes.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        double maxDown = changes.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);

        vo.setUp_count(up);
        vo.setDown_count(down);
        vo.setFlat_count(flat);
        vo.setUp_ratio(BigDecimal.valueOf((double) up * 100.0 / matchedCodes.size()).setScale(2, BigDecimal.ROUND_HALF_UP));
        vo.setMedian_change(BigDecimal.valueOf(median).setScale(3, BigDecimal.ROUND_HALF_UP));
        vo.setMax_up(BigDecimal.valueOf(maxUp).setScale(3, BigDecimal.ROUND_HALF_UP));
        vo.setMax_down(BigDecimal.valueOf(maxDown).setScale(3, BigDecimal.ROUND_HALF_UP));
        vo.setLimit_up_count(limitUp);
        vo.setLimit_down_count(limitDown);
        vo.setMatched_stocks(matchedNames);
        return vo;
    }

    private boolean isLimitUp(StockDaily sd, double change) {
        return change >= limitThreshold(sd, true);
    }

    private boolean isLimitDown(StockDaily sd, double change) {
        return change <= -limitThreshold(sd, false);
    }

    private double limitThreshold(StockDaily sd, boolean up) {
        String code = sd.getStockCode() == null ? "" : sd.getStockCode();
        String name = sd.getStockName() == null ? "" : sd.getStockName().toUpperCase(Locale.ROOT);
        // 用户约定：ST/*ST 仍按 10%；创业板/科创板按 20%；主板按 10%。
        if (name.startsWith("ST") || name.startsWith("*ST")) return 9.5;
        if (code.startsWith("300") || code.startsWith("688")) return 19.5;
        return 9.5;
    }

    private double computeMedian(List<Double> list) {
        if (list.isEmpty()) return 0.0;
        int n = list.size();
        if (n % 2 == 1) {
            return list.get(n / 2);
        } else {
            return (list.get(n/2 - 1) + list.get(n/2)) / 2.0;
        }
    }
}
