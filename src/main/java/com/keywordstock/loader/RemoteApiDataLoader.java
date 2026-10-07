package com.keywordstock.loader;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keywordstock.entity.KeywordStatsEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class RemoteApiDataLoader implements DataLoader {

    private final RestTemplate rest = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${app.remote.sample-url:https://raw.githubusercontent.com/CN1921/-/main/sample-data/keyword_stats_sample.json}")
    private String sampleUrl;

    @Override
    public List<KeywordStatsEntity> fetch(String keyword, LocalDate from, LocalDate to) throws Exception {
        // For demo the loader fetches a JSON array from a configurable URL and filters by keyword/date range.
        ResponseEntity<String> resp = rest.getForEntity(sampleUrl, String.class);
        if (!resp.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("failed to fetch remote data: " + resp.getStatusCode());
        }
        String body = resp.getBody();
        List<Map<String, Object>> list = mapper.readValue(body, new TypeReference<List<Map<String, Object>>>(){});
        List<KeywordStatsEntity> out = new ArrayList<>();
        for (Map<String,Object> m : list) {
            String k = (String) m.get("keyword");
            String dateStr = (String) m.get("statDate");
            Number mentions = (Number) m.get("mentions");
            Number score = (Number) m.get("score");
            if (k == null || dateStr == null) continue;
            if (keyword != null && !keyword.isEmpty() && !k.contains(keyword)) continue;
            KeywordStatsEntity e = new KeywordStatsEntity();
            e.setKeyword(k);
            e.setStatDate(LocalDate.parse(dateStr));
            e.setMentions(mentions == null ? 0L : mentions.longValue());
            e.setScore(score == null ? 0.0 : score.doubleValue());
            out.add(e);
        }
        return out;
    }
}
