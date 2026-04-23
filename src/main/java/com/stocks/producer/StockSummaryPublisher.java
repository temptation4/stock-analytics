package com.stocks.producer;

import com.stocks.model.StockSummary;
import com.stocks.service.StockQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Publishes the latest derived stock summaries back to Kafka so downstream
 * consumers can read processed analytics without querying the state stores.
 */
@Component
public class StockSummaryPublisher {

    private static final Logger log = LoggerFactory.getLogger(StockSummaryPublisher.class);
    private static final String TOPIC = "stock-summaries";

    @Autowired
    private KafkaTemplate<String, StockSummary> kafkaTemplate;

    @Autowired
    private StockQueryService queryService;

    @Scheduled(fixedDelay = 1000)
    public void publishSummaries() {
        try {
            List<StockSummary> summaries = queryService.getAllSummaries();
            for (StockSummary summary : summaries) {
                kafkaTemplate.send(TOPIC, summary.getSymbol(), summary);
            }
        } catch (Exception e) {
            log.debug("Summary publish skipped: {}", e.getMessage());
        }
    }
}
