package com.keywordstock.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.keywordstock.model.TrendPoint;
import com.keywordstock.model.TrendResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

/**
 * TrendService: 构建关键词趋势数据
 *
 * 优先从 AKShare 桥接服务获取概念板块历史走势;
 * 如果桥接不可用，降级到内存样例数据。
 */
@Service
public class TrendService {

    private final DataLoader dataLoader;
    private final AkShareBridgeClient bridgeClient;
    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    public TrendService(DataLoader dataLoader, AkShareBridgeClient bridgeClient) {
        this.dataLoader = dataLoader;
        this.bridgeClient = bridgeClient;
    }

    /**
     * 返回关键词最近 N 天的趋势数据
     */
    @SuppressWarnings("unchecked")
    public TrendResult getTrend(String keyword, int days) {
        if (keyword == null) keyword = "";
        String kw = keyword.trim();
        if (kw.isEmpty()) {
            return new TrendResult("", Collections.emptyList());
        }

        // 优先从 AKShare 桥接获取
        if (bridgeClient.isBridgeAvailable()) {
            try {
                String json = bridgeClient.fetchTrend(kw, days);
                if (json != null) {
                    Map<String, Object> resp = mapper.readValue(json, Map.class);
                    List<Map<String, Object>> pointsRaw = (List<Map<String, Object>>) resp.get("points");
                    String concept = (String) resp.getOrDefault("concept", "");
                    if (pointsRaw != null && !pointsRaw.isEmpty()) {
                        List<TrendPoint> points = new ArrayList<>();
                        for (Map<String, Object> p : pointsRaw) {
                            String date = (String) p.get("date");
                            int count = toInt(p.get("count"));
                            double ratio = toDouble(p.get("ratio"));
                            points.add(new TrendPoint(date, count, ratio));
                        }
                        String label = concept.isEmpty() ? kw : kw + " (" + concept + ")";
                        return new TrendResult(label, points);
                    }
                }
            } catch (Exception e) {
                // 降级
            }
        }

        // 降级: 使用内存样例数据
        return getTrendFromSample(kw, days);
    }

    private TrendResult getTrendFromSample(String keyword, int days) {
        String key = keyword.toLowerCase();
        NavigableMap<LocalDate, Integer> map = dataLoader.getHistoricalKeywordCounts().get(key);

        List<TrendPoint> points = new ArrayList<>();
        LocalDate today = LocalDate.now();

        if (map == null) {
            for (int i = days - 1; i >= 0; i--) {
                LocalDate d = today.minusDays(i);
                points.add(new TrendPoint(d.toString(), 0, 0.0));
            }
            return new TrendResult(keyword, points);
        }

        List<LocalDate> dates = new ArrayList<>(map.keySet());
        int available = dates.size();
        int start = Math.max(0, available - days);
        List<LocalDate> selected = dates.subList(start, available);

        for (LocalDate d : selected) {
            int count = map.getOrDefault(d, 0);
            points.add(new TrendPoint(d.toString(), count, 0.0));
        }
        return new TrendResult(keyword, points);
    }

    private int toInt(Object val) {
        if (val == null) return 0;
        if (val instanceof Number) return ((Number) val).intValue();
        try { return Integer.parseInt(val.toString()); } catch (Exception e) { return 0; }
    }

    private double toDouble(Object val) {
        if (val == null) return 0.0;
        if (val instanceof Number) return ((Number) val).doubleValue();
        try { return Double.parseDouble(val.toString()); } catch (Exception e) { return 0.0; }
    }
}
