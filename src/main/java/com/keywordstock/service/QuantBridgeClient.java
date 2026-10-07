package com.keywordstock.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Java facade for the Python AI-Quant foundation. */
@Component
public class QuantBridgeClient {
    private final RestTemplate rest = new RestTemplate();

    @Autowired
    private AkShareBridgeClient bridgeClient;

    public String health() {
        return get("/api/quant/health");
    }

    public String features(String code, double eventStrength, double eventConfidence,
                           double eventDirection, double nameResonance, double conceptHeat) {
        String url = "/api/quant/features?code=" + enc(code)
                + "&event_strength=" + eventStrength
                + "&event_confidence=" + eventConfidence
                + "&event_direction=" + eventDirection
                + "&name_resonance=" + nameResonance
                + "&concept_heat=" + conceptHeat;
        return get(url);
    }

    public String history(String code, String startDate, String endDate, String adjust) {
        String url = "/api/quant/history?code=" + enc(code)
                + "&start_date=" + enc(startDate)
                + "&end_date=" + enc(endDate)
                + "&adjust=" + enc(adjust);
        return get(url);
    }

    public String post(String path, String body) {
        try {
            String base = bridgeClient.getBridgeUrl();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            return rest.postForObject(base + path, new HttpEntity<>(body, headers), String.class);
        } catch (Exception e) {
            return null;
        }
    }

    private String get(String path) {
        try {
            String base = bridgeClient.getBridgeUrl();
            return rest.getForObject(base + path, String.class);
        } catch (Exception e) {
            return null;
        }
    }

    private String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
