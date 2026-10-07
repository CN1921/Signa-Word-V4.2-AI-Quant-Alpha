package com.keywordstock.repository;

import com.keywordstock.entity.KeywordStatsEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface KeywordStatsRepository extends JpaRepository<KeywordStatsEntity, Long> {
    Page<KeywordStatsEntity> findByKeywordContainingIgnoreCase(String keyword, Pageable pageable);
    Page<KeywordStatsEntity> findByKeywordContainingIgnoreCaseAndStatDateBetween(String keyword, LocalDate from, LocalDate to, Pageable pageable);
    Page<KeywordStatsEntity> findByStatDateGreaterThanEqual(LocalDate from, Pageable pageable);
    Page<KeywordStatsEntity> findByStatDateLessThanEqual(LocalDate to, Pageable pageable);
    Page<KeywordStatsEntity> findByStatDateBetween(LocalDate from, LocalDate to, Pageable pageable);
}
