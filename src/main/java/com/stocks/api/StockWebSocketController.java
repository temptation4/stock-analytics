package com.stocks.api;

import com.stocks.model.StockSummary;
import com.stocks.service.StockQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Pushes live stock summaries to all WebSocket subscribers every second.
 * Clients subscribe to /topic/stocks and receive a JSON array of StockSummary.
 */
@Component
public class StockWebSocketController {

    private static final Logger log = LoggerFactory.getLogger(StockWebSocketController.class);

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private StockQueryService queryService;

    @Scheduled(fixedDelay = 1000)
    public void pushUpdates() {
        try {
            List<StockSummary> summaries = queryService.getAllSummaries();
            if (!summaries.isEmpty()) {
                messagingTemplate.convertAndSend("/topic/stocks", summaries);
            }
        } catch (Exception e) {
            log.debug("WebSocket push skipped: {}", e.getMessage());
        }
    }
}
