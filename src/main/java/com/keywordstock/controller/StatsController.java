package com.keywordstock.controller;

import com.keywordstock.entity.KeywordStatsEntity;
import com.keywordstock.model.Result;
import com.keywordstock.service.StatsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.constraints.Min;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/stats")
@Validated
public class StatsController {

    @Autowired
    private StatsService service;

    @GetMapping
    public Result<Page<KeywordStatsEntity>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        Pageable p = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "statDate"));
        Page<KeywordStatsEntity> res = service.query(keyword, from, to, p);
        return Result.ok(res);
    }
}
