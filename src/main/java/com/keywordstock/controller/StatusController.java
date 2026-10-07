package com.keywordstock.controller;

import com.keywordstock.model.Result;
import com.keywordstock.service.AkShareBridgeClient;
import com.keywordstock.service.DataLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 数据状态查询接口
 *
 * GET /api/status - 查看数据加载状态
 */
@RestController
@RequestMapping("/api/status")
public class StatusController {

    @Autowired
    private DataLoader dataLoader;

    @Autowired
    private AkShareBridgeClient bridgeClient;

    @GetMapping
    public Result<Map<String, Object>> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("dataStatus", dataLoader.getDataStatus());
        status.put("liveData", dataLoader.isLiveData());
        status.put("bridgeAvailable", bridgeClient.isBridgeAvailable());
        status.put("stockCount", dataLoader.getStockCount());
        status.put("conceptStockCount", dataLoader.getConceptStockCount());
        status.put("conceptDataSource", dataLoader.getConceptDataSource());
        return Result.ok(status);
    }
}
