package com.keywordstock.service;

import com.keywordstock.entity.KeywordStatsEntity;
import com.keywordstock.loader.DataLoader;
import com.keywordstock.repository.KeywordStatsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class StatsService {

    @Autowired
    private KeywordStatsRepository repository;

    @Autowired
    private DataLoader dataLoader; // will be autowired to RemoteApiDataLoader

    @Cacheable(value = "keywordStats", key = "(#keyword == null ? '' : #keyword) + '_' + (#from == null ? '' : #from) + '_' + (#to == null ? '' : #to) + '_' + #pageable.pageNumber + '_' + #pageable.pageSize")
    public Page<KeywordStatsEntity> query(String keyword, LocalDate from, LocalDate to, Pageable pageable) {
        String k = keyword == null ? "" : keyword.trim();
        if (from != null && to != null) return repository.findByKeywordContainingIgnoreCaseAndStatDateBetween(k, from, to, pageable);
        if (from != null) return repository.findByStatDateGreaterThanEqual(from, pageable);
        if (to != null) return repository.findByStatDateLessThanEqual(to, pageable);
        return repository.findByKeywordContainingIgnoreCase(k, pageable);
    }

    @Transactional
    @CacheEvict(value = "keywordStats", allEntries = true)
    public void refreshFromRemote(String keyword, LocalDate from, LocalDate to) throws Exception {
        List<KeywordStatsEntity> list = dataLoader.fetch(keyword, from, to);
        if (list != null && !list.isEmpty()) {
            repository.saveAll(list);
        }
    }
}
