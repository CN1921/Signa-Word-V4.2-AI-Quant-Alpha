package com.keywordstock.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.io.File;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * AKShare 桥接客户端
 *
 * 负责与 Python AKShare 桥接服务通信，支持自动启动 Python 服务。
 * 如果 Python 服务不可用，Java 端会降级到内存样例数据。
 */
@Component
public class AkShareBridgeClient {

    private static final Logger logger = Logger.getLogger(AkShareBridgeClient.class.getName());

    private final RestTemplate rest = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${app.akshare.bridge-url:http://127.0.0.1:5555}")
    private String bridgeUrl;

    @Value("${app.akshare.auto-start:true}")
    private boolean autoStart;

    @Value("${app.akshare.start-timeout-seconds:60}")
    private int startTimeoutSeconds;

    private volatile boolean bridgeAvailable = false;

    @PostConstruct
    public void init() {
        if (checkHealth()) {
            logger.info("AKShare 桥接服务已就绪");
            return;
        }
        if (autoStart) {
            logger.info("AKShare 桥接服务未运行，尝试自动启动...");
            startBridge();
        } else {
            logger.warning("AKShare 桥接服务未运行且未启用自动启动，将使用样例数据");
        }
    }

    public boolean isBridgeAvailable() {
        return bridgeAvailable;
    }

    /**
     * 检查桥接服务健康状态
     */
    public boolean checkHealth() {
        try {
            ResponseEntity<String> resp = rest.getForEntity(bridgeUrl + "/health", String.class);
            if (resp.getStatusCode().is2xxSuccessful()) {
                bridgeAvailable = true;
                return true;
            }
        } catch (Exception e) {
            // 服务未运行
        }
        bridgeAvailable = false;
        return false;
    }

    /**
     * 自动启动 Python 桥接服务
     */
    private void startBridge() {
        String scriptPath = findScriptPath();
        if (scriptPath == null) {
            logger.warning("未找到 akshare_server.py，跳过自动启动。将使用样例数据。");
            return;
        }

        String[] pythonCmds = {"python", "python3", "py"};
        Process process = null;

        for (String cmd : pythonCmds) {
            try {
                ProcessBuilder pb = new ProcessBuilder(cmd, scriptPath);
                pb.redirectErrorStream(true);
                pb.directory(new File(System.getProperty("user.dir")));
                // 设置环境变量，确保 Python 输出不被缓冲
                pb.environment().put("PYTHONUNBUFFERED", "1");
                process = pb.start();

                // 启动一个线程读取输出（防止进程阻塞）
                final Process p = process;
                Thread outputReader = new Thread(() -> {
                    try {
                        byte[] buf = new byte[4096];
                        int len;
                        while ((len = p.getInputStream().read(buf)) != -1) {
                            String line = new String(buf, 0, len);
                            if (line.trim().length() > 0) {
                                logger.info("[Python] " + line.trim());
                            }
                        }
                    } catch (Exception ignored) {
                    }
                });
                outputReader.setDaemon(true);
                outputReader.start();

                logger.info("正在等待 Python 桥接服务启动 (最多 " + startTimeoutSeconds + " 秒)...");
                int waited = 0;
                int interval = 2000;
                while (waited < startTimeoutSeconds * 1000) {
                    Thread.sleep(interval);
                    waited += interval;
                    if (checkHealth()) {
                        logger.info("Python 桥接服务启动成功! (耗时 " + (waited / 1000) + " 秒)");
                        return;
                    }
                }
                // 超时
                if (process.isAlive()) {
                    logger.warning("Python 进程在运行但健康检查超时，可能是 AKShare 正在加载数据。");
                    bridgeAvailable = true; // 进程在运行，可能只是数据还在加载
                    return;
                }
            } catch (Exception e) {
                logger.log(Level.FINE, "使用 " + cmd + " 启动失败: " + e.getMessage(), e);
            }
        }

        logger.warning("无法自动启动 Python 桥接服务，将使用样例数据。");
        logger.warning("可手动启动: 运行 python-bridge/start_server.bat");
    }

    private String findScriptPath() {
        String userDir = System.getProperty("user.dir");
        String[] candidates = {
                userDir + File.separator + "python-bridge" + File.separator + "akshare_server.py",
                userDir + File.separator + ".." + File.separator + "python-bridge" + File.separator + "akshare_server.py",
                "python-bridge" + File.separator + "akshare_server.py"
        };
        for (String path : candidates) {
            File f = new File(path);
            if (f.exists()) {
                return f.getAbsolutePath();
            }
        }
        return null;
    }

    /**
     * 获取实时行情数据
     * 返回 JSON 字符串，由调用方解析
     */
    public String fetchRealtimeStocks() {
        try {
            ResponseEntity<String> resp = rest.getForEntity(bridgeUrl + "/api/stocks/realtime", String.class);
            if (resp.getStatusCode().is2xxSuccessful()) {
                return resp.getBody();
            }
        } catch (Exception e) {
            logger.warning("获取实时行情失败: " + e.getMessage());
        }
        return null;
    }

    /**
     * 获取概念板块映射
     */
    public String fetchConcepts() {
        try {
            ResponseEntity<String> resp = rest.getForEntity(bridgeUrl + "/api/concepts", String.class);
            if (resp.getStatusCode().is2xxSuccessful()) {
                return resp.getBody();
            }
        } catch (Exception e) {
            logger.warning("获取概念板块失败: " + e.getMessage());
        }
        return null;
    }

    /**
     * 获取关键词趋势数据
     */
    public String fetchTrend(String keyword, int days) {
        try {
            String url = bridgeUrl + "/api/trend?keyword=" + java.net.URLEncoder.encode(keyword, "UTF-8")
                    + "&days=" + days;
            ResponseEntity<String> resp = rest.getForEntity(url, String.class);
            if (resp.getStatusCode().is2xxSuccessful()) {
                return resp.getBody();
            }
        } catch (Exception e) {
            logger.warning("获取趋势数据失败: " + e.getMessage());
        }
        return null;
    }

    /**
     * 刷新实时数据
     */
    public void refreshRealtime() {
        try {
            rest.getForEntity(bridgeUrl + "/api/stocks/refresh", String.class);
        } catch (Exception e) {
            logger.warning("刷新实时数据失败: " + e.getMessage());
        }
    }
    public String getBridgeUrl() {
        return bridgeUrl;
    }

}
