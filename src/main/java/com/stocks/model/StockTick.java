package com.stocks.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StockTick {
    private String symbol;
    private double price;
    private long volume;
    private long timestamp;
    private double changePercent;

    public StockTick() {}

    public StockTick(String symbol, double price, long volume, long timestamp, double changePercent) {
        this.symbol = symbol;
        this.price = price;
        this.volume = volume;
        this.timestamp = timestamp;
        this.changePercent = changePercent;
    }

    public static Builder builder() { return new Builder(); }

    public String getSymbol()        { return symbol; }
    public double getPrice()         { return price; }
    public long   getVolume()        { return volume; }
    public long   getTimestamp()     { return timestamp; }
    public double getChangePercent() { return changePercent; }

    public void setSymbol(String symbol)              { this.symbol = symbol; }
    public void setPrice(double price)                { this.price = price; }
    public void setVolume(long volume)                { this.volume = volume; }
    public void setTimestamp(long timestamp)          { this.timestamp = timestamp; }
    public void setChangePercent(double changePercent){ this.changePercent = changePercent; }

    public static class Builder {
        private String symbol;
        private double price;
        private long   volume;
        private long   timestamp;
        private double changePercent;

        public Builder symbol(String v)        { symbol = v; return this; }
        public Builder price(double v)         { price = v; return this; }
        public Builder volume(long v)          { volume = v; return this; }
        public Builder timestamp(long v)       { timestamp = v; return this; }
        public Builder changePercent(double v) { changePercent = v; return this; }

        public StockTick build() {
            return new StockTick(symbol, price, volume, timestamp, changePercent);
        }
    }
}
