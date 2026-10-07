package com.keywordstock.controller;

import com.keywordstock.model.ConceptRankItem;
import com.keywordstock.model.Result;
import com.keywordstock.service.ConceptService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * /api/concept/top?threshold=0&topN=10&date=2026-08-03
 */
@RestController
@RequestMapping("/api/concept")
public class ConceptController {

    @Autowired
    private ConceptService conceptService;

    @GetMapping("/top")
    public Result<List<ConceptRankItem>> getConceptTop(
            @RequestParam(defaultValue = "0") double threshold,
            @RequestParam(defaultValue = "10") int topN,
            @RequestParam(required = false) String date) {
        if (topN <= 0) topN = 10;
        List<ConceptRankItem> items = conceptService.topConcepts(threshold, topN);
        return Result.ok(items);
    }
}
