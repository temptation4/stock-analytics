package com.stocks.streams;

import com.stocks.model.OhlcvCandle;
import com.stocks.model.StockTick;
import com.stocks.serde.JsonSerde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.WindowStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Kafka Streams Topology — three sub-topologies branch from the same source stream:
 *
 *  1. Latest price KTable  → state store "latest-price-store"
 *  2. 1-minute OHLCV KTable → state store "ohlcv-store"
 *  3. 5-minute moving-average KTable → state store "moving-avg-store"
 */
@Component
public class StockStreamsTopology {

    public static final String TOPIC_TICKS      = "stock-ticks";
    public static final String TOPIC_OHLCV      = "stock-ohlcv-1min";

    public static final String STORE_LATEST     = "latest-price-store";
    public static final String STORE_OHLCV      = "ohlcv-store";
    public static final String STORE_MOVING_AVG = "moving-avg-store";

    private static final JsonSerde<StockTick>            TICK_SERDE   = new JsonSerde<>(StockTick.class);
    private static final JsonSerde<OhlcvCandle>          OHLCV_SERDE  = new JsonSerde<>(OhlcvCandle.class);
    private static final JsonSerde<MovingAvgAccumulator> AVG_SERDE    = new JsonSerde<>(MovingAvgAccumulator.class);

    @Autowired
    public void buildTopology(StreamsBuilder builder) {

        // ── Source stream ──────────────────────────────────────────────────────
        KStream<String, StockTick> ticks = builder.stream(
                TOPIC_TICKS,
                Consumed.with(Serdes.String(), TICK_SERDE)
        );

        // ── 1. Latest price per symbol ─────────────────────────────────────────
        // Each new tick replaces the previous value in a persistent KV state store.
        ticks
            .groupByKey(Grouped.with(Serdes.String(), TICK_SERDE))
            .aggregate(
                StockTick::new,
                (symbol, newTick, ignored) -> newTick,
                Materialized.<String, StockTick, KeyValueStore<Bytes, byte[]>>as(STORE_LATEST)
                    .withKeySerde(Serdes.String())
                    .withValueSerde(TICK_SERDE)
            );

        // ── 2. 1-minute OHLCV candles ─────────────────────────────────────────
        // Tumbling window: non-overlapping 1-minute buckets.
        KTable<Windowed<String>, OhlcvCandle> ohlcvTable = ticks
            .groupByKey(Grouped.with(Serdes.String(), TICK_SERDE))
            .windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofMinutes(1)))
            .aggregate(
                OhlcvCandle::new,
                (symbol, tick, candle) -> candle.update(tick),
                Materialized.<String, OhlcvCandle, WindowStore<Bytes, byte[]>>as(STORE_OHLCV)
                    .withKeySerde(Serdes.String())
                    .withValueSerde(OHLCV_SERDE)
            );

        // Sink completed candles to output topic
        ohlcvTable
            .toStream()
            .selectKey((windowed, candle) -> windowed.key())
            .to(TOPIC_OHLCV, Produced.with(Serdes.String(), OHLCV_SERDE));

        // ── 3. 5-minute hopping moving average ────────────────────────────────
        // Hopping window (5 min size, 1 min advance) → rolling mean per symbol.
        ticks
            .groupByKey(Grouped.with(Serdes.String(), TICK_SERDE))
            .windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofMinutes(5))
                .advanceBy(Duration.ofMinutes(1)))
            .aggregate(
                MovingAvgAccumulator::new,
                (symbol, tick, acc) -> acc.add(tick.getPrice()),
                Materialized.<String, MovingAvgAccumulator, WindowStore<Bytes, byte[]>>as(STORE_MOVING_AVG)
                    .withKeySerde(Serdes.String())
                    .withValueSerde(AVG_SERDE)
            );
    }

    // ── Accumulator for moving average ────────────────────────────────────────
    public static class MovingAvgAccumulator {
        public double sum   = 0.0;
        public long   count = 0L;

        public MovingAvgAccumulator add(double price) {
            sum += price;
            count++;
            return this;
        }

        public double average() {
            return count == 0 ? 0.0 : Math.round((sum / count) * 100.0) / 100.0;
        }
    }
}
