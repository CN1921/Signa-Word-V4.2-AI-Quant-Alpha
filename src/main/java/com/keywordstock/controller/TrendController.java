package com.keywordstock.controller;

import com.keywordstock.model.Result;
import com.keywordstock.model.TrendResult;
import com.keywordstock.service.TrendService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * /api/trend?keyword=新能源&days=20&date=2026-08-03
 */
@RestController
@RequestMapping("/api")
public class TrendController {

    @Autowired
    private TrendService trendService;

    @GetMapping("/trend")
    public Result<TrendResult> getTrend(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "20") int days,
            @RequestParam(required = false) String date) {
        if (days <= 0) days = 20;
        TrendResult r = trendService.getTrend(keyword, days);
        return Result.ok(r);
    }
}
