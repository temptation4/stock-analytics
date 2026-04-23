# Stock Analytics Design Document

## Overview

`stock-analytics` is a Spring Boot application that demonstrates real-time stream
processing with Apache Kafka Streams. It simulates stock and crypto price ticks,
processes them into derived analytics, materializes queryable state stores, and
exposes the results through REST APIs, Kafka summary topics, Prometheus metrics,
and Grafana dashboards.

This project is designed as a learning and demo system rather than a
production-grade market data platform. The current implementation uses generated
tick data, a single Kafka broker, in-memory scheduling, and a local Docker-based
deployment model.

## Goals

- Demonstrate Kafka Streams concepts end to end in a runnable application.
- Show how stream processing can feed both APIs and dashboards.
- Materialize state in local stores and expose it through Interactive Queries.
- Provide a simple local environment for experimentation with Kafka, Prometheus,
  and Grafana.

## Non-Goals

- Real market data ingestion from an external provider.
- Multi-broker Kafka or highly available deployment.
- Exactly-once financial processing guarantees.
- Authentication, authorization, tenant isolation, or hardened production ops.

## High-Level Architecture

The system is composed of five logical layers:

1. Data generation
   - `StockPriceProducer` creates mock ticks every 500 ms.
2. Stream processing
   - `StockStreamsTopology` consumes `stock-ticks` and derives aggregates.
3. Query layer
   - `StockQueryService` reads Kafka Streams state stores via Interactive Queries.
4. Delivery layer
   - REST endpoints expose current and historical views.
   - A scheduled Kafka publisher emits derived summaries to a Kafka topic.
5. Observability layer
   - Prometheus scrapes metrics from the app.
   - Grafana visualizes those metrics.

## Runtime Components

### Application

- Framework: Spring Boot 3.2
- Language level in Maven: Java 17
- Embedded web server: Tomcat
- Exposed application port: `8070`

### Local Infrastructure

Defined in `docker-compose.yml`:

- Zookeeper
- Kafka (`localhost:9092`)
- Prometheus (`localhost:9090`)
- Grafana (`localhost:3000`)

## Main Data Flow

The core pipeline looks like this:

1. `StockPriceProducer` generates a `StockTick` for each symbol.
2. The tick is published to Kafka topic `stock-ticks`.
3. Kafka Streams consumes `stock-ticks` as a `KStream<String, StockTick>`.
4. The stream is aggregated into three materialized views:
   - latest tick per symbol
   - 1-minute OHLCV candles
   - 5-minute moving average
5. These materialized views are stored in Kafka Streams state stores.
6. `StockQueryService` reads those stores on demand.
7. The app publishes results through:
   - REST endpoints under `/api/stocks`
   - Kafka topic `stock-summaries`
   - Prometheus metrics at `/actuator/prometheus`

## Topics and State Stores

### Kafka Topics

- `stock-ticks`
  - input topic containing raw generated ticks
- `stock-ohlcv-1min`
  - output topic containing derived 1-minute candle data
- `stock-summaries`
  - output topic containing current summary snapshots for downstream consumers

Kafka Streams also creates internal changelog and repartition topics as needed
for stateful processing.

### State Stores

The topology materializes three named stores:

- `latest-price-store`
  - type: key-value store
  - purpose: most recent `StockTick` for each symbol

- `ohlcv-store`
  - type: window store
  - purpose: 1-minute OHLCV candles per symbol

- `moving-avg-store`
  - type: window store
  - purpose: rolling 5-minute moving average per symbol

These stores are the backing data source for both the REST API and the Kafka
summary publisher.

## Domain Model

### `StockTick`

Represents a single market event:

- `symbol`
- `price`
- `volume`
- `timestamp`
- `changePercent`

### `OhlcvCandle`

Represents a 1-minute aggregate:

- `open`
- `high`
- `low`
- `close`
- `volume`
- `windowStart`

### `StockSummary`

Represents the compact view used by clients:

- `symbol`
- `price`
- `changePercent`
- `volume`
- `movingAvg5min`
- `timestamp`

## Producer Design

`StockPriceProducer` is a scheduled component that emits mock data every 500 ms.
It loops over a fixed set of symbols and uses a random-walk formula with mild
mean reversion to derive the next price.

Characteristics:

- deterministic symbol universe
- generated values around hardcoded base prices
- no external API dependency
- one Kafka message per symbol per cycle

Implication:

The analytics are "live" with respect to Kafka processing, but the source data
is simulated, not real market data.

## Stream Processing Design

The topology is built in `StockStreamsTopology`.

### Source Stream

The source is:

- topic: `stock-ticks`
- key: `String` symbol
- value: `StockTick`

### Latest Price Aggregate

The app groups by symbol and keeps the latest `StockTick`. This creates a
queryable point-in-time view for current price, volume, and percentage change.

### OHLCV Aggregate

The app uses a 1-minute tumbling window to build candle data. Each tick updates
the active candle for its symbol and minute bucket. Completed candles are also
written to `stock-ohlcv-1min`.

### Moving Average Aggregate

The app uses a 5-minute hopping window with 1-minute advance. This creates a
rolling average that updates frequently while still representing a 5-minute
window.

## Query Layer Design

`StockQueryService` provides a thin abstraction over Kafka Streams Interactive
Queries.

Responsibilities:

- obtain the running `KafkaStreams` instance from `StreamsBuilderFactoryBean`
- verify that the stream is in a queryable state
- read from named stores using `StoreQueryParameters`
- transform raw store values into API-friendly responses

The service protects callers from transient state-store issues by handling:

- `InvalidStateStoreException`
- `IllegalStateException`

This prevents scheduled metric and summary-publisher tasks from failing noisily while
the stream is starting, rebalancing, or recovering.

## API Design

REST endpoints are exposed under `/api/stocks`.

### `GET /api/stocks`

Returns summary data for all tracked symbols.

### `GET /api/stocks/{symbol}/latest`

Returns the latest raw `StockTick` for one symbol.

### `GET /api/stocks/{symbol}/candles?limit=30`

Returns recent OHLCV candles for one symbol.

### `GET /api/stocks/{symbol}/moving-avg`

Returns the current 5-minute moving average for one symbol.

### Design Notes

- Symbol input is normalized to uppercase.
- API data is read from state stores, not by consuming Kafka again.
- This keeps query latency low and avoids duplicating consumer logic.

## Kafka Summary Publisher Design

`StockSummaryPublisher` runs on a schedule and queries the current summaries from
Kafka Streams state stores. It publishes each summary to the `stock-summaries`
topic using `KafkaTemplate`.

Why this exists:

- downstream services can consume processed analytics directly from Kafka
- the project no longer depends on a custom browser dashboard layer
- the design stays consistent with a Kafka-centric architecture

## Metrics and Dashboard Design

`StockMetricsExporter` converts state-store values into Micrometer gauges.

Exported metrics:

- `stock_price{symbol=...}`
- `stock_change_percent{symbol=...}`
- `stock_volume{symbol=...}`
- `stock_moving_avg_5min{symbol=...}`

Prometheus scrapes:

- `http://host.docker.internal:8070/actuator/prometheus`

Grafana uses those metrics to render:

- live prices
- percentage change
- moving average
- per-symbol stat panels
- comparison panels across all symbols

The dashboard currently uses full display names such as `Apple`, `Amazon`, and
`Bitcoin`, while metrics still retain ticker symbols as labels.

## Serialization Strategy

The project uses a custom generic `JsonSerde<T>` built on Jackson.

Why:

- reusable for multiple model types
- simple to reason about in a demo app
- avoids extra schema infrastructure

Trade-off:

- it is less robust than schema-based formats such as Avro or Protobuf
- malformed topic records can cause deserialization failures

To reduce operational fragility, the Kafka Streams configuration now uses:

- `LogAndContinueExceptionHandler`

This means malformed records are logged and skipped instead of shutting down the
entire Streams client.

## Configuration

Important runtime settings:

- app port: `8070`
- Kafka bootstrap server: `localhost:9092`
- Streams application id: `stock-analytics-app`
- Streams state dir: `/tmp/kafka-streams/stock-analytics`
- Prometheus scrape interval: `5s`

## Operational Behavior

### Startup

1. Spring Boot starts the web app.
2. Kafka Streams initializes and restores state if needed.
3. Scheduled producer starts emitting ticks.
4. Metrics exporter and summary publisher start reading summaries.
5. Prometheus scrapes the app.
6. Grafana reads from Prometheus.

### Failure Modes Encountered

During development, the following issues were important:

- Kafka not available at `localhost:9092`
- port conflicts on `8070`
- malformed records such as `hi` or `hello` in `stock-ticks`
- querying stores while Streams is not running
- Prometheus scraping the wrong application port

The current design includes fixes for these cases, especially:

- deserialization errors are skipped
- query service handles non-queryable stream states
- Prometheus is configured to scrape `8070`

## Trade-Offs and Limitations

### Strengths

- simple local setup
- demonstrates multiple Kafka Streams patterns
- queryable state stores are exposed cleanly
- easy to demo through REST and Grafana

### Limitations

- generated data rather than real market feeds
- single-node Kafka and single app instance
- no persistent external database
- no auth/security
- local state stores make horizontal scaling more complex
- window retention and topic lifecycle are not tuned for long-term history

## Extension Points

Natural next steps for the project:

- replace simulated producer with a real market data ingestion service
- add a proper Kafka UI such as AKHQ
- add schema management for messages
- persist longer-term historical data to a database or warehouse
- add health checks for Kafka Streams state
- add tests around topology behavior and query logic
- support symbol metadata so APIs can return both ticker and full display name

## Summary

This project is a compact real-time analytics system built around Kafka Streams.
Its key idea is that a single stream-processing topology can power multiple
consumers:

- APIs
- Kafka summary consumers
- Prometheus metrics
- Grafana dashboards

The project is most useful as:

- a Kafka Streams learning project
- a real-time Grafana analytics demo
- a starter architecture for stream-derived queryable views

It should be viewed as a well-instrumented demo platform today, and a base for
more production-like streaming work if extended with real data ingestion,
stronger serialization, and operational hardening.
