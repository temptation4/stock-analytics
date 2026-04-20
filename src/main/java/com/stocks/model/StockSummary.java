package com.stocks.model;

public class StockSummary {
    private String symbol;
    private double price;
    private double changePercent;
    private long   volume;
    private double movingAvg5min;
    private long   timestamp;

    public StockSummary() {}

    public StockSummary(String symbol, double price, double changePercent,
                        long volume, double movingAvg5min, long timestamp) {
        this.symbol       = symbol;
        this.price        = price;
        this.changePercent = changePercent;
        this.volume       = volume;
        this.movingAvg5min = movingAvg5min;
        this.timestamp    = timestamp;
    }

    public static Builder builder() { return new Builder(); }

    public String getSymbol()        { return symbol; }
    public double getPrice()         { return price; }
    public double getChangePercent() { return changePercent; }
    public long   getVolume()        { return volume; }
    public double getMovingAvg5min() { return movingAvg5min; }
    public long   getTimestamp()     { return timestamp; }

    public void setSymbol(String v)         { symbol = v; }
    public void setPrice(double v)          { price = v; }
    public void setChangePercent(double v)  { changePercent = v; }
    public void setVolume(long v)           { volume = v; }
    public void setMovingAvg5min(double v)  { movingAvg5min = v; }
    public void setTimestamp(long v)        { timestamp = v; }

    public static class Builder {
        private String symbol;
        private double price;
        private double changePercent;
        private long   volume;
        private double movingAvg5min;
        private long   timestamp;

        public Builder symbol(String v)        { symbol = v; return this; }
        public Builder price(double v)         { price = v; return this; }
        public Builder changePercent(double v) { changePercent = v; return this; }
        public Builder volume(long v)          { volume = v; return this; }
        public Builder movingAvg5min(double v) { movingAvg5min = v; return this; }
        public Builder timestamp(long v)       { timestamp = v; return this; }

        public StockSummary build() {
            return new StockSummary(symbol, price, changePercent, volume, movingAvg5min, timestamp);
        }
    }
}
