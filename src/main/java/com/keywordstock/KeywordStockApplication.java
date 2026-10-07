package com.keywordstock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class KeywordStockApplication {
    public static void main(String[] args) {
        SpringApplication.run(KeywordStockApplication.class, args);
    }
}
