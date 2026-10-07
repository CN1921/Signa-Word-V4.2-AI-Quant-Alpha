package com.keywordstock.loader;

import com.keywordstock.entity.KeywordStatsEntity;
import java.time.LocalDate;
import java.util.List;

public interface DataLoader {
    List<KeywordStatsEntity> fetch(String keyword, LocalDate from, LocalDate to) throws Exception;
}
