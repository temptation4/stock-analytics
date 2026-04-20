package com.stocks.api;

import com.stocks.model.OhlcvCandle;
import com.stocks.model.StockSummary;
import com.stocks.model.StockTick;
import com.stocks.service.StockQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API that exposes Kafka Streams state store data.
 * Uses Interactive Queries — no Kafka consumer needed.
 */
@RestController
@RequestMapping("/api/stocks")
@CrossOrigin(origins = "*")
public class StockRestController {

    @Autowired
    private StockQueryService queryService;

    /** All symbols with latest price, % change, moving average */
    @GetMapping
    public List<StockSummary> getAllStocks() {
        return queryService.getAllSummaries();
    }

    /** Latest raw tick for a single symbol */
    @GetMapping("/{symbol}/latest")
    public ResponseEntity<StockTick> getLatest(@PathVariable String symbol) {
        return queryService.getLatestTick(symbol.toUpperCase())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** Last N 1-minute OHLCV candles (default 30) */
    @GetMapping("/{symbol}/candles")
    public List<OhlcvCandle> getCandles(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "30") int limit) {
        return queryService.getCandles(symbol.toUpperCase(), limit);
    }

    /** Current 5-minute moving average price */
    @GetMapping("/{symbol}/moving-avg")
    public double getMovingAvg(@PathVariable String symbol) {
        return queryService.getMovingAvgForSymbol(symbol.toUpperCase());
    }
}
