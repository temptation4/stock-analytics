package com.stocks.metrics;

import com.stocks.model.StockSummary;
import com.stocks.service.StockQueryService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Exports live Kafka Streams state store data as Prometheus Gauges.
 * Micrometer registers each metric with a "symbol" tag — Grafana can then
 * filter / group by symbol in any panel.
 *
 * Metrics exposed:
 *   stock_price{symbol}          — latest price
 *   stock_change_percent{symbol} — % change from day open
 *   stock_volume{symbol}         — volume of last tick
 *   stock_moving_avg_5min{symbol}— 5-minute moving average price
 */
@Component
public class StockMetricsExporter {

    // Latest values cached here; Gauges read these via lambda
    private final Map<String, Double> prices      = new ConcurrentHashMap<>();
    private final Map<String, Double> changes     = new ConcurrentHashMap<>();
    private final Map<String, Double> volumes     = new ConcurrentHashMap<>();
    private final Map<String, Double> movingAvgs  = new ConcurrentHashMap<>();

    private static final List<String> SYMBOLS = List.of(
            "AAPL","GOOGL","MSFT","AMZN","TSLA",
            "NVDA","META","NFLX","BTC","ETH"
    );

    @Autowired
    public StockMetricsExporter(MeterRegistry registry, StockQueryService queryService) {
        // Register one Gauge per symbol per metric — they read from the maps above
        for (String symbol : SYMBOLS) {
            prices.put(symbol, 0.0);
            changes.put(symbol, 0.0);
            volumes.put(symbol, 0.0);
            movingAvgs.put(symbol, 0.0);

            Gauge.builder("stock_price", prices, m -> m.getOrDefault(symbol, 0.0))
                    .tag("symbol", symbol)
                    .description("Latest stock price")
                    .register(registry);

            Gauge.builder("stock_change_percent", changes, m -> m.getOrDefault(symbol, 0.0))
                    .tag("symbol", symbol)
                    .description("Percentage change from day open")
                    .register(registry);

            Gauge.builder("stock_volume", volumes, m -> m.getOrDefault(symbol, 0.0))
                    .tag("symbol", symbol)
                    .description("Volume of latest tick")
                    .register(registry);

            Gauge.builder("stock_moving_avg_5min", movingAvgs, m -> m.getOrDefault(symbol, 0.0))
                    .tag("symbol", symbol)
                    .description("5-minute moving average price")
                    .register(registry);
        }
    }

    /** Refresh the cached values from Kafka Streams state stores every second */
    @Autowired
    private StockQueryService queryService;

    @Scheduled(fixedDelay = 1000)
    public void refresh() {
        List<StockSummary> summaries = queryService.getAllSummaries();
        for (StockSummary s : summaries) {
            prices.put(s.getSymbol(),     s.getPrice());
            changes.put(s.getSymbol(),    s.getChangePercent());
            volumes.put(s.getSymbol(),    (double) s.getVolume());
            movingAvgs.put(s.getSymbol(), s.getMovingAvg5min());
        }
    }
}
