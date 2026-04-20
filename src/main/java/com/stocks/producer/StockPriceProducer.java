package com.stocks.producer;

import com.stocks.model.StockTick;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simulates price ticks for 10 stock/crypto symbols using a random walk.
 * Each tick fires every 500 ms across all symbols (staggered via symbol index).
 */
@Component
public class StockPriceProducer {

    private static final Logger log = LoggerFactory.getLogger(StockPriceProducer.class);
    private static final String TOPIC = "stock-ticks";
    private static final Random RNG = new Random();

    /** Base prices for each simulated symbol */
    private static final Map<String, Double> BASE_PRICES = Map.of(
            "AAPL",  185.0,
            "GOOGL", 140.0,
            "MSFT",  375.0,
            "AMZN",  178.0,
            "TSLA",  245.0,
            "NVDA",  870.0,
            "META",  500.0,
            "NFLX",  610.0,
            "BTC",   65000.0,
            "ETH",   3500.0
    );

    /** Tracks the last emitted price for % change calculation */
    private final Map<String, Double> lastPrices = new ConcurrentHashMap<>(BASE_PRICES);

    /** Tracks "day open" price — reset once; used for daily % change */
    private final Map<String, Double> openPrices = new ConcurrentHashMap<>(BASE_PRICES);

    @Autowired
    private KafkaTemplate<String, StockTick> kafkaTemplate;

    /**
     * Fires every 500 ms. Each call produces one tick per symbol —
     * 10 messages total, keeping the stream lively.
     */
    @Scheduled(fixedDelay = 500)
    public void produceTicks() {
        long now = System.currentTimeMillis();
        for (String symbol : BASE_PRICES.keySet()) {
            double lastPrice = lastPrices.get(symbol);
            double basePrice = BASE_PRICES.get(symbol);

            // Random walk: ±0.3 % drift, with slight mean-reversion to base
            double drift = (RNG.nextGaussian() * 0.003) + 0.0002 * (basePrice - lastPrice) / basePrice;
            double newPrice = Math.max(lastPrice * (1 + drift), 0.01);
            newPrice = Math.round(newPrice * 100.0) / 100.0;

            double changePercent = ((newPrice - openPrices.get(symbol)) / openPrices.get(symbol)) * 100;
            changePercent = Math.round(changePercent * 100.0) / 100.0;

            long volume = (long) (RNG.nextInt(1000) + 100);

            StockTick tick = StockTick.builder()
                    .symbol(symbol)
                    .price(newPrice)
                    .volume(volume)
                    .timestamp(now)
                    .changePercent(changePercent)
                    .build();

            kafkaTemplate.send(TOPIC, symbol, tick);
            lastPrices.put(symbol, newPrice);

            log.debug("Produced tick: {} @ {}", symbol, newPrice);
        }
    }
}
