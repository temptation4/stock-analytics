package com.stocks.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class OhlcvCandle {
    private String symbol;
    private double open;
    private double high;
    private double low;
    private double close;
    private long   volume;
    private long   windowStart;

    public OhlcvCandle() {}

    public OhlcvCandle(String symbol, double open, double high, double low,
                       double close, long volume, long windowStart) {
        this.symbol      = symbol;
        this.open        = open;
        this.high        = high;
        this.low         = low;
        this.close       = close;
        this.volume      = volume;
        this.windowStart = windowStart;
    }

    /** Called for each tick that falls in the current tumbling window. */
    public OhlcvCandle update(StockTick tick) {
        if (symbol == null) {
            symbol      = tick.getSymbol();
            open        = tick.getPrice();
            high        = tick.getPrice();
            low         = tick.getPrice();
            windowStart = tick.getTimestamp();
        }
        high   = Math.max(high, tick.getPrice());
        low    = Math.min(low,  tick.getPrice());
        close  = tick.getPrice();
        volume += tick.getVolume();
        return this;
    }

    public String getSymbol()      { return symbol; }
    public double getOpen()        { return open; }
    public double getHigh()        { return high; }
    public double getLow()         { return low; }
    public double getClose()       { return close; }
    public long   getVolume()      { return volume; }
    public long   getWindowStart() { return windowStart; }

    public void setSymbol(String v)      { symbol = v; }
    public void setOpen(double v)        { open = v; }
    public void setHigh(double v)        { high = v; }
    public void setLow(double v)         { low = v; }
    public void setClose(double v)       { close = v; }
    public void setVolume(long v)        { volume = v; }
    public void setWindowStart(long v)   { windowStart = v; }
}
