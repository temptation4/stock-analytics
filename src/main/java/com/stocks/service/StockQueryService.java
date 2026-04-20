package com.stocks.service;

import com.stocks.model.OhlcvCandle;
import com.stocks.model.StockSummary;
import com.stocks.model.StockTick;
import com.stocks.streams.StockStreamsTopology;
import com.stocks.streams.StockStreamsTopology.MovingAvgAccumulator;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.errors.InvalidStateStoreException;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.streams.state.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * Uses Kafka Streams Interactive Queries to read directly from RocksDB state
 * stores — no extra Kafka consumer or topic needed.
 */
@Service
public class StockQueryService {

    private static final Logger log = LoggerFactory.getLogger(StockQueryService.class);

    private static final List<String> SYMBOLS = List.of(
            "AAPL", "GOOGL", "MSFT", "AMZN", "TSLA",
            "NVDA", "META", "NFLX", "BTC", "ETH"
    );

    @Autowired
    private StreamsBuilderFactoryBean factoryBean;

    private KafkaStreams streams() {
        KafkaStreams kafkaStreams = Objects.requireNonNull(factoryBean.getKafkaStreams(),
                "Kafka Streams has not been initialized yet");
        KafkaStreams.State state = kafkaStreams.state();
        if (!state.isRunningOrRebalancing()) {
            throw new IllegalStateException("Kafka Streams is not queryable yet. State is " + state);
        }
        return kafkaStreams;
    }

    // ── 1. All symbols summary ─────────────────────────────────────────────────
    public List<StockSummary> getAllSummaries() {
        List<StockSummary> result = new ArrayList<>();
        try {
            ReadOnlyKeyValueStore<String, StockTick> latestStore =
                    streams().store(StoreQueryParameters.fromNameAndType(
                            StockStreamsTopology.STORE_LATEST,
                            QueryableStoreTypes.keyValueStore()));

            ReadOnlyWindowStore<String, MovingAvgAccumulator> avgStore =
                    streams().store(StoreQueryParameters.fromNameAndType(
                            StockStreamsTopology.STORE_MOVING_AVG,
                            QueryableStoreTypes.windowStore()));

            for (String symbol : SYMBOLS) {
                StockTick tick = latestStore.get(symbol);
                if (tick == null) continue;

                double movingAvg = fetchLatestAvg(avgStore, symbol);

                result.add(StockSummary.builder()
                        .symbol(symbol)
                        .price(tick.getPrice())
                        .changePercent(tick.getChangePercent())
                        .volume(tick.getVolume())
                        .movingAvg5min(movingAvg)
                        .timestamp(tick.getTimestamp())
                        .build());
            }
        } catch (InvalidStateStoreException | IllegalStateException e) {
            log.debug("State store not ready yet: {}", e.getMessage());
        }
        return result;
    }

    // ── 2. Latest tick for one symbol ─────────────────────────────────────────
    public Optional<StockTick> getLatestTick(String symbol) {
        try {
            ReadOnlyKeyValueStore<String, StockTick> store =
                    streams().store(StoreQueryParameters.fromNameAndType(
                            StockStreamsTopology.STORE_LATEST,
                            QueryableStoreTypes.keyValueStore()));
            return Optional.ofNullable(store.get(symbol));
        } catch (InvalidStateStoreException | IllegalStateException e) {
            log.debug("State store not ready: {}", e.getMessage());
            return Optional.empty();
        }
    }

    // ── 3. Last N OHLCV candles for one symbol ────────────────────────────────
    public List<OhlcvCandle> getCandles(String symbol, int limit) {
        List<OhlcvCandle> candles = new ArrayList<>();
        try {
            ReadOnlyWindowStore<String, OhlcvCandle> store =
                    streams().store(StoreQueryParameters.fromNameAndType(
                            StockStreamsTopology.STORE_OHLCV,
                            QueryableStoreTypes.windowStore()));

            Instant from = Instant.now().minusSeconds(60L * limit);
            Instant to   = Instant.now().plusSeconds(60);

            try (WindowStoreIterator<OhlcvCandle> it = store.fetch(symbol, from, to)) {
                while (it.hasNext()) {
                    KeyValue<Long, OhlcvCandle> kv = it.next();
                    if (kv.value != null) {
                        kv.value.setWindowStart(kv.key);
                        candles.add(kv.value);
                    }
                }
            }

            candles.sort(Comparator.comparingLong(OhlcvCandle::getWindowStart));
            if (candles.size() > limit) {
                candles = candles.subList(candles.size() - limit, candles.size());
            }
        } catch (InvalidStateStoreException | IllegalStateException e) {
            log.debug("OHLCV store not ready: {}", e.getMessage());
        }
        return candles;
    }

    // ── 4. Current 5-min moving average for one symbol ────────────────────────
    public double getMovingAvgForSymbol(String symbol) {
        try {
            ReadOnlyWindowStore<String, MovingAvgAccumulator> store =
                    streams().store(StoreQueryParameters.fromNameAndType(
                            StockStreamsTopology.STORE_MOVING_AVG,
                            QueryableStoreTypes.windowStore()));
            return fetchLatestAvg(store, symbol);
        } catch (InvalidStateStoreException | IllegalStateException e) {
            log.debug("Moving avg store not ready: {}", e.getMessage());
            return 0.0;
        }
    }

    // ── Internal: pick the most recent window for the symbol ──────────────────
    private double fetchLatestAvg(ReadOnlyWindowStore<String, MovingAvgAccumulator> store,
                                  String symbol) {
        Instant from = Instant.now().minusSeconds(300);
        Instant to   = Instant.now().plusSeconds(1);
        double latestAvg = 0.0;
        long   latestKey = Long.MIN_VALUE;
        try (WindowStoreIterator<MovingAvgAccumulator> it = store.fetch(symbol, from, to)) {
            while (it.hasNext()) {
                KeyValue<Long, MovingAvgAccumulator> kv = it.next();
                if (kv.value != null && kv.key >= latestKey) {
                    latestKey  = kv.key;
                    latestAvg  = kv.value.average();
                }
            }
        } catch (Exception e) {
            log.debug("Could not fetch moving avg for {}: {}", symbol, e.getMessage());
        }
        return latestAvg;
    }
}
