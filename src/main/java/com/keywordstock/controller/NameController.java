package com.keywordstock.controller;

import com.keywordstock.model.KeywordStatsVO;
import com.keywordstock.model.Result;
import com.keywordstock.service.KeywordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

/**
 * /api/name/stats?keyword=华&date=2026-08-03
 */
@RestController
@RequestMapping("/api/name")
public class NameController {

    @Autowired
    private KeywordService keywordService;

    @GetMapping("/stats")
    public Result<List<KeywordStatsVO>> queryNameStats(
            @RequestParam String keyword,
            @RequestParam(required = false) String date) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return Result.fail("keyword required");
        }
        // reuse keyword service with scope=name
        List<KeywordStatsVO> res = keywordService.computeKeywordStats(Collections.singletonList(keyword), "name");
        return Result.ok(res);
    }
}
