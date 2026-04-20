package com.stocks;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class StockAnalyticsApplication {
    public static void main(String[] args) {
        SpringApplication.run(StockAnalyticsApplication.class, args);
    }
}
