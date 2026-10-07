package com.keywordstock.scheduler;

import com.keywordstock.service.StatsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class StatsPrecomputeJob {

    @Autowired
    private StatsService service;

    // Run once a day at 02:00
    @Scheduled(cron = "0 0 2 * * ?")
    public void runDaily() {
        try {
            LocalDate today = LocalDate.now();
            // Example: refresh for a few example keywords; in real usage fetch the keyword list dynamically
            String[] keywords = new String[]{"人工智能", "区块链", "新能源"};
            for (String k : keywords) {
                service.refreshFromRemote(k, today.minusDays(7), today);
            }
        } catch (Exception ex) {
            // log but don't throw
            ex.printStackTrace();
        }
    }
}
