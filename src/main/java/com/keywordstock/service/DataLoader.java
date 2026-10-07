package com.keywordstock.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.keywordstock.model.StockDaily;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * DataLoader: 股票数据加载器
 *
 * 优先从 AKShare 桥接服务获取实盘 A 股数据;
 * 如果桥接服务不可用，自动降级到内存样例数据，保证服务始终可用。
 *
 * 数据包含:
 * 1. todayStockMap  - 全 A 股实时行情 (代码 -> StockDaily)
 * 2. stockConcepts  - 股票概念板块映射 (代码 -> [概念名])
 */
@Component
public class DataLoader {

    private static final Logger logger = Logger.getLogger(DataLoader.class.getName());

    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    private AkShareBridgeClient bridgeClient;

    // 全 A 股实时行情
    private final Map<String, StockDaily> todayStockMap = new ConcurrentHashMap<>();

    // 股票代码 -> 概念列表
    private final Map<String, List<String>> stockConcepts = new ConcurrentHashMap<>();

    // 历史关键词趋势数据 (keyword -> date -> count)
    private final Map<String, NavigableMap<LocalDate, Integer>> historicalKeywordCounts = new ConcurrentHashMap<>();

    // 数据是否来自实盘
    private volatile boolean liveData = false;

    // 数据加载状态: loading / ready / fallback
    private volatile String dataStatus = "init";

    // 概念数据来源: none / live / sample。实盘模式下禁止保留 sample 概念。
    private volatile String conceptDataSource = "none";

    public Map<String, StockDaily> getTodayStockMap() { return todayStockMap; }
    public Map<String, List<String>> getStockConcepts() { return stockConcepts; }
    public Map<String, NavigableMap<LocalDate, Integer>> getHistoricalKeywordCounts() { return historicalKeywordCounts; }
    public boolean isLiveData() { return liveData; }
    public String getDataStatus() { return dataStatus; }
    public int getStockCount() { return todayStockMap.size(); }
    public int getConceptStockCount() { return stockConcepts.size(); }
    public String getConceptDataSource() { return conceptDataSource; }

    @PostConstruct
    public void init() {
        dataStatus = "loading";
        if (bridgeClient.isBridgeAvailable()) {
            loadFromBridge();
        }
        if (todayStockMap.isEmpty()) {
            logger.warning("实盘数据加载失败，降级到样例行情");
            loadSampleData();
            // 只有桥接服务完全不可用时才允许加载概念样例。
            // 桥接可用但实时/概念接口暂时失败时，必须保持概念为空，避免把 mock 当真实数据。
            if (!bridgeClient.isBridgeAvailable()) {
            }
            dataStatus = "fallback";
            // 启动后台延迟重试: Python 桥接可能仍在加载数据
            scheduleRetry();
        } else {
            dataStatus = "ready";
        }
    }

    /**
     * 后台延迟重试加载实盘数据 (桥接服务可能刚启动, 数据还在加载中)
     */
    private void scheduleRetry() {
        Thread retryThread = new Thread(() -> {
            int[] delays = {10, 20, 30, 40, 50, 60};
            for (int delay : delays) {
                try {
                    Thread.sleep(delay * 1000L);
                } catch (InterruptedException e) {
                    return;
                }
                if (!"fallback".equals(dataStatus)) {
                    return; // 已经切换到实盘数据
                }
                logger.info("重试加载实盘数据 (延迟 " + delay + "s)...");
                if (bridgeClient.isBridgeAvailable()) {
                    loadFromBridge();
                    if (liveData) {
                        dataStatus = "ready";
                        logger.info("延迟重试成功! 已切换到实盘数据: " + todayStockMap.size() + " 只股票");
                        return;
                    }
                }
            }
            logger.warning("延迟重试已用尽，继续使用样例数据。将在下次定时刷新时重试。");
        }, "data-retry");
        retryThread.setDaemon(true);
        retryThread.start();
    }

    /**
     * 从 AKShare 桥接服务加载实盘数据
     */
    @SuppressWarnings("unchecked")
    private void loadFromBridge() {
        logger.info("正在从 AKShare 桥接服务加载实盘数据...");

        // 1. 加载实时行情
        try {
            String json = bridgeClient.fetchRealtimeStocks();
            if (json != null) {
                Map<String, Object> resp = mapper.readValue(json, Map.class);
                List<Map<String, Object>> stocks = (List<Map<String, Object>>) resp.get("stocks");
                if (stocks != null && !stocks.isEmpty()) {
                    todayStockMap.clear();
                    for (Map<String, Object> s : stocks) {
                        String code = (String) s.get("code");
                        String name = (String) s.get("name");
                        if (code == null || code.isEmpty() || name == null || name.isEmpty()) continue;

                        StockDaily sd = new StockDaily();
                        sd.setStockCode(code);
                        sd.setStockName(name);
                        sd.setOpen(toBigDecimal(s.get("open")));
                        sd.setClose(toBigDecimal(s.get("close")));
                        sd.setHigh(toBigDecimal(s.get("high")));
                        sd.setLow(toBigDecimal(s.get("low")));
                        sd.setChangePct(toBigDecimal(s.get("changePct")));
                        sd.setVolume(toLong(s.get("volume")));
                        sd.setAmount(toLong(s.get("amount")));
                        todayStockMap.put(code, sd);
                    }
                    logger.info("实盘行情加载成功: " + todayStockMap.size() + " 只股票");
                    liveData = true;
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "加载实时行情异常: " + e.getMessage(), e);
        }

        // 2. 加载真实概念板块映射（禁止模拟关系）
        try {
            String json = bridgeClient.fetchConcepts();
            if (json != null) {
                Map<String, Object> resp = mapper.readValue(json, Map.class);
                Map<String, List<String>> s2c = (Map<String, List<String>>) resp.get("stock_to_concepts");
                if (s2c != null && !s2c.isEmpty()) {
                    stockConcepts.clear();
                    stockConcepts.putAll(s2c);
                    conceptDataSource = "live";
                    logger.info("概念板块映射加载成功: " + stockConcepts.size() + " 只股票有概念标签");
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "加载概念板块异常: " + e.getMessage(), e);
        }

        // Python 概念构建可能需要较长时间，后续定时任务会继续尝试加载。
        // 概念数据严格遵循真实来源；不可用时保持为空，绝不生成模拟概念关系。
        if (stockConcepts.isEmpty()) {
            logger.info("真实概念数据尚未就绪，conceptDataSource=none，等待 AKShare 概念构建完成。");
        }
    }

    /**
     * 定时检查并加载真实概念映射。AKShare 首次构建概念可能耗时数分钟，
     * 因此 Java 启动时如果拿到空映射，不应立即用 mock 数据覆盖。
     */
    @Scheduled(fixedDelay = 30000)
    public void scheduledConceptRefresh() {
        if (!bridgeClient.isBridgeAvailable()) return;
        try {
            String json = bridgeClient.fetchConcepts();
            if (json == null || json.isEmpty()) return;
            Map<String, Object> resp = mapper.readValue(json, Map.class);
            Object status = resp.get("status");
            Map<String, List<String>> s2c = (Map<String, List<String>>) resp.get("stock_to_concepts");
            if (s2c != null && !s2c.isEmpty()) {
                stockConcepts.clear();
                stockConcepts.putAll(s2c);
                conceptDataSource = "live";
                logger.info("真实概念板块映射加载成功: " + stockConcepts.size() + " 只股票有概念标签，status=" + status);
            } else if ("building".equals(String.valueOf(status))) {
                logger.fine("AKShare 概念仍在后台构建中...");
            }
        } catch (Exception e) {
            logger.log(Level.FINE, "定时加载概念映射异常: " + e.getMessage(), e);
        }
    }

    /**
     * 定时刷新实时行情 (每 60 秒, 仅在交易日时段)
     */
    @Scheduled(fixedDelay = 60000)
    public void scheduledRefresh() {
        if (!bridgeClient.isBridgeAvailable()) {
            return;
        }
        // fallback 模式下随时刷新; 正常模式仅在交易时段刷新
        if (!"fallback".equals(dataStatus)) {
            int hour = java.time.LocalTime.now(java.time.ZoneId.of("Asia/Shanghai")).getHour();
            if (hour < 8 || hour > 16) {
                return;
            }
        }
        try {
            String json = bridgeClient.fetchRealtimeStocks();
            if (json != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> resp = mapper.readValue(json, Map.class);
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> stocks = (List<Map<String, Object>>) resp.get("stocks");
                if (stocks != null && !stocks.isEmpty()) {
                    int count = 0;
                    for (Map<String, Object> s : stocks) {
                        String code = (String) s.get("code");
                        String name = (String) s.get("name");
                        if (code == null || code.isEmpty()) continue;
                        StockDaily sd = new StockDaily();
                        sd.setStockCode(code);
                        sd.setStockName(name != null ? name : "");
                        sd.setOpen(toBigDecimal(s.get("open")));
                        sd.setClose(toBigDecimal(s.get("close")));
                        sd.setHigh(toBigDecimal(s.get("high")));
                        sd.setLow(toBigDecimal(s.get("low")));
                        sd.setChangePct(toBigDecimal(s.get("changePct")));
                        sd.setVolume(toLong(s.get("volume")));
                        sd.setAmount(toLong(s.get("amount")));
                        todayStockMap.put(code, sd);
                        count++;
                    }
                    liveData = true;
                    if ("fallback".equals(dataStatus)) {
                        dataStatus = "ready";
                    }
                    logger.fine("定时刷新完成: " + count + " 只股票");
                }
            }
        } catch (Exception e) {
            logger.log(Level.FINE, "定时刷新异常: " + e.getMessage(), e);
        }
    }

    // ---------- 样例数据 (降级方案) ----------

    private void loadSampleData() {
        todayStockMap.clear();
        addStock("300750", "宁德时代", new BigDecimal("320.50"), new BigDecimal("3.45"));
        addStock("002594", "比亚迪", new BigDecimal("260.33"), new BigDecimal("4.20"));
        addStock("000858", "五粮液", new BigDecimal("220.10"), new BigDecimal("-1.2"));
        addStock("600519", "贵州茅台", new BigDecimal("1850.00"), new BigDecimal("0.5"));
        addStock("600100", "同花顺", new BigDecimal("45.20"), new BigDecimal("-9.95"));
        addStock("000001", "平安银行", new BigDecimal("12.30"), new BigDecimal("10.00"));
        seedHistoricalCounts(Arrays.asList("新能源", "新能源车", "锂电池", "华为", "白酒", "科技", "华"));
        logger.info("样例数据加载完成: " + todayStockMap.size() + " 只股票");
    }

    private void addStock(String code, String name, BigDecimal close, BigDecimal changePct) {
        StockDaily s = new StockDaily(code, name, close, changePct);
        todayStockMap.put(code, s);
    }

    private void seedHistoricalCounts(List<String> keywords) {
        Random rand = new Random(123456L);
        LocalDate today = LocalDate.now();
        int days = 60;
        for (String kw : keywords) {
            NavigableMap<LocalDate, Integer> map = new TreeMap<>();
            for (int i = days - 1; i >= 0; i--) {
                LocalDate d = today.minusDays(i);
                int base = Math.abs(kw.hashCode()) % 10 + rand.nextInt(5);
                int noise = rand.nextInt(10);
                int value = Math.max(0, base + (i % 7) - (i / 20) + noise);
                map.put(d, value);
            }
            historicalKeywordCounts.put(kw.toLowerCase(), map);
        }
    }

    // ---------- 工具方法 ----------

    private BigDecimal toBigDecimal(Object val) {
        if (val == null) return BigDecimal.ZERO;
        if (val instanceof Number) {
            return BigDecimal.valueOf(((Number) val).doubleValue());
        }
        try {
            return new BigDecimal(val.toString());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private long toLong(Object val) {
        if (val == null) return 0L;
        if (val instanceof Number) {
            return ((Number) val).longValue();
        }
        try {
            return Long.parseLong(val.toString());
        } catch (Exception e) {
            return 0L;
        }
    }
}
