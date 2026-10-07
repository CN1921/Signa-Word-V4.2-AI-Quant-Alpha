package com.keywordstock.controller;

import com.keywordstock.service.QuantBridgeClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/** AI-quant foundation endpoints. */
@RestController
@RequestMapping("/api/quant")
public class QuantController {
    @Autowired
    private QuantBridgeClient quantBridgeClient;

    @GetMapping("/health")
    public Object health() {
        return quantBridgeClient.health();
    }

    @GetMapping("/features")
    public Object features(
            @RequestParam String code,
            @RequestParam(defaultValue = "0") double eventStrength,
            @RequestParam(defaultValue = "0") double eventConfidence,
            @RequestParam(defaultValue = "0") double eventDirection,
            @RequestParam(defaultValue = "0") double nameResonance,
            @RequestParam(defaultValue = "0") double conceptHeat) {
        return quantBridgeClient.features(code, eventStrength, eventConfidence,
                eventDirection, nameResonance, conceptHeat);
    }

    @GetMapping("/history")
    public Object history(
            @RequestParam String code,
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam(defaultValue = "qfq") String adjust) {
        return quantBridgeClient.history(code, startDate, endDate, adjust);
    }

    @PostMapping("/dataset")
    public Object dataset(@RequestBody String body) {
        return quantBridgeClient.post("/api/quant/dataset", body);
    }

    @PostMapping("/train")
    public Object train(@RequestBody String body) {
        return quantBridgeClient.post("/api/quant/train", body);
    }

    @PostMapping("/backtest")
    public Object backtest(@RequestBody String body) {
        return quantBridgeClient.post("/api/quant/backtest", body);
    }
}
