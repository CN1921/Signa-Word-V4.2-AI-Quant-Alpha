package com.keywordstock.controller;

import com.keywordstock.model.KeywordStatsVO;
import com.keywordstock.model.Result;
import com.keywordstock.service.KeywordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * /api/keyword/stats?keywords=新能源,地产&date=2026-08-03&scope=both
 */
@RestController
@RequestMapping("/api/keyword")
public class KeywordController {

    @Autowired
    private KeywordService keywordService;

    @GetMapping("/stats")
    public Result<List<KeywordStatsVO>> queryKeywordStats(
            @RequestParam String keywords,
            @RequestParam(required = false) String date,
            @RequestParam(defaultValue = "both") String scope) {
        if (keywords == null || keywords.trim().isEmpty()) {
            return Result.fail("keywords required");
        }
        List<String> list = Arrays.stream(keywords.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
        List<KeywordStatsVO> data = keywordService.computeKeywordStats(list, scope);
        return Result.ok(data);
    }
}
