/* ─────────────────────────────────────────────────────────────────
   app.js  –  Kafka Streams Stock Dashboard
   Connects via SockJS/STOMP WebSocket to receive live stock summaries
   and fetches OHLCV candle data via REST for the selected symbol.
   ───────────────────────────────────────────────────────────────── */

'use strict';

const API_BASE = '/api/stocks';
const WS_URL   = '/ws';

// ── State ────────────────────────────────────────────────────────
let selectedSymbol = null;
let candleChart    = null;
let volumeChart    = null;
let lastSummaries  = [];
let candlePollTimer = null;

// ── Clock ────────────────────────────────────────────────────────
function startClock() {
    function tick() {
        document.getElementById('clock').textContent =
            new Date().toLocaleTimeString('en-US', { hour12: false });
    }
    tick();
    setInterval(tick, 1000);
}

// ── WebSocket (STOMP over SockJS) ────────────────────────────────
function connectWebSocket() {
    const client = new StompJs.Client({
        webSocketFactory: () => new SockJS(WS_URL),
        reconnectDelay: 3000,
        onConnect: () => {
            setWsStatus(true);
            client.subscribe('/topic/stocks', frame => {
                const summaries = JSON.parse(frame.body);
                lastSummaries = summaries;
                renderCards(summaries);
                renderTicker(summaries);
                renderTables(summaries);
            });
        },
        onDisconnect: () => setWsStatus(false),
        onStompError:  () => setWsStatus(false),
    });
    client.activate();
}

function setWsStatus(online) {
    const el = document.getElementById('ws-status');
    el.textContent = online ? '● Live' : '● Offline';
    el.className = 'badge ' + (online ? 'badge-online' : 'badge-offline');
}

// ── Ticker Bar ───────────────────────────────────────────────────
function renderTicker(summaries) {
    const items = [...summaries, ...summaries]; // duplicate for infinite scroll
    const html = items.map(s => {
        const dir = s.changePercent > 0 ? 'up' : s.changePercent < 0 ? 'down' : 'flat';
        const sign = s.changePercent > 0 ? '+' : '';
        return `<span class="ticker-item">
            <span class="ticker-symbol">${s.symbol}</span>
            <span class="ticker-price">${formatPrice(s.symbol, s.price)}</span>
            <span class="ticker-change ${dir}">${sign}${s.changePercent.toFixed(2)}%</span>
        </span>`;
    }).join('');
    document.getElementById('ticker-content').innerHTML = html;
}

// ── Symbol Cards ─────────────────────────────────────────────────
function renderCards(summaries) {
    const grid = document.getElementById('cards-grid');
    const prevSelected = selectedSymbol;

    // Build or update cards (keyed by symbol)
    summaries.forEach(s => {
        let card = document.getElementById('card-' + s.symbol);
        if (!card) {
            card = document.createElement('div');
            card.id = 'card-' + s.symbol;
            card.className = 'card';
            card.addEventListener('click', () => selectSymbol(s.symbol));
            grid.appendChild(card);
        }

        const dir   = s.changePercent > 0 ? 'up' : s.changePercent < 0 ? 'down' : 'flat';
        const sign  = s.changePercent > 0 ? '+' : '';
        const arrow = s.changePercent > 0 ? '▲' : s.changePercent < 0 ? '▼' : '—';

        card.innerHTML = `
            <div class="card-symbol">${s.symbol}</div>
            <div class="card-price">${formatPrice(s.symbol, s.price)}</div>
            <div class="card-change ${dir}">${arrow} ${sign}${s.changePercent.toFixed(2)}%</div>
            <div class="card-vol">Vol ${s.volume.toLocaleString()}</div>
        `;
        card.className = 'card' + (s.symbol === prevSelected ? ' active' : '');
    });
}

// ── Gainers / Losers Tables ──────────────────────────────────────
function renderTables(summaries) {
    const sorted = [...summaries].sort((a, b) => b.changePercent - a.changePercent);
    const gainers = sorted.filter(s => s.changePercent >= 0).slice(0, 5);
    const losers  = sorted.filter(s => s.changePercent <  0).slice(-5).reverse();

    fillTable('gainers-table', gainers, 'up');
    fillTable('losers-table',  losers,  'down');
}

function fillTable(id, items, dir) {
    const tbody = document.querySelector('#' + id + ' tbody');
    tbody.innerHTML = items.map(s => {
        const sign = s.changePercent > 0 ? '+' : '';
        return `<tr>
            <td><strong>${s.symbol}</strong></td>
            <td>${formatPrice(s.symbol, s.price)}</td>
            <td class="${dir}">${sign}${s.changePercent.toFixed(2)}%</td>
        </tr>`;
    }).join('');
}

// ── Symbol Selection ─────────────────────────────────────────────
function selectSymbol(symbol) {
    if (selectedSymbol === symbol) return;

    // Update active card
    if (selectedSymbol) {
        const prev = document.getElementById('card-' + selectedSymbol);
        if (prev) prev.classList.remove('active');
    }
    const next = document.getElementById('card-' + symbol);
    if (next) next.classList.add('active');

    selectedSymbol = symbol;
    document.getElementById('chart-title').textContent = symbol + ' — 1-min Candles';
    document.getElementById('chart-subtitle').textContent = 'Last 30 minutes • Kafka Streams OHLCV';
    document.getElementById('ma-badge').style.display = 'block';

    // Fetch once immediately then poll
    fetchAndRenderCandles(symbol);
    clearInterval(candlePollTimer);
    candlePollTimer = setInterval(() => fetchAndRenderCandles(symbol), 5000);
}

// ── Candle Fetch & Chart ─────────────────────────────────────────
async function fetchAndRenderCandles(symbol) {
    try {
        const [candlesRes, maRes] = await Promise.all([
            fetch(`${API_BASE}/${symbol}/candles?limit=30`),
            fetch(`${API_BASE}/${symbol}/moving-avg`),
        ]);

        const candles = await candlesRes.json();
        const ma      = await maRes.json();

        document.getElementById('ma-value').textContent =
            ma > 0 ? formatPrice(symbol, ma) : '—';

        renderCandleChart(candles, ma);
        renderVolumeChart(candles);
    } catch (e) {
        console.warn('Failed to fetch candles:', e);
    }
}

function renderCandleChart(candles, ma) {
    const ctx = document.getElementById('candleChart').getContext('2d');

    const ohlcData = candles.map(c => ({
        x: c.windowStart,
        o: c.open,
        h: c.high,
        l: c.low,
        c: c.close,
    }));

    const maData = candles.map(c => ({
        x: c.windowStart,
        y: ma > 0 ? ma : null,
    }));

    if (candleChart) {
        candleChart.data.datasets[0].data = ohlcData;
        candleChart.data.datasets[1].data = maData;
        candleChart.update('none');
        return;
    }

    candleChart = new Chart(ctx, {
        data: {
            datasets: [
                {
                    type: 'candlestick',
                    label: 'OHLCV',
                    data: ohlcData,
                    color: {
                        up: '#3fb950',
                        down: '#f85149',
                        unchanged: '#8b949e',
                    },
                },
                {
                    type: 'line',
                    label: 'MA(5m)',
                    data: maData,
                    borderColor: '#d29922',
                    borderWidth: 1.5,
                    borderDash: [4, 4],
                    pointRadius: 0,
                    tension: 0.3,
                },
            ],
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: {
                    labels: { color: '#8b949e', font: { size: 12 } },
                },
                tooltip: {
                    callbacks: {
                        label: ctx => {
                            const d = ctx.raw;
                            if (d && d.o !== undefined) {
                                return [
                                    `O: ${d.o}`, `H: ${d.h}`,
                                    `L: ${d.l}`, `C: ${d.c}`,
                                ];
                            }
                            return `MA: ${d.y}`;
                        },
                    },
                },
            },
            scales: {
                x: {
                    type: 'time',
                    time: { unit: 'minute', displayFormats: { minute: 'HH:mm' } },
                    ticks: { color: '#8b949e', maxTicksLimit: 8 },
                    grid: { color: '#21262d' },
                },
                y: {
                    position: 'right',
                    ticks: { color: '#8b949e' },
                    grid: { color: '#21262d' },
                },
            },
        },
    });
}

function renderVolumeChart(candles) {
    const ctx = document.getElementById('volumeChart').getContext('2d');

    const volData = candles.map(c => ({
        x: c.windowStart,
        y: c.volume,
    }));

    const colors = candles.map(c =>
        c.close >= c.open ? 'rgba(63,185,80,.6)' : 'rgba(248,81,73,.6)'
    );

    if (volumeChart) {
        volumeChart.data.datasets[0].data   = volData;
        volumeChart.data.datasets[0].backgroundColor = colors;
        volumeChart.update('none');
        return;
    }

    volumeChart = new Chart(ctx, {
        type: 'bar',
        data: {
            datasets: [{
                label: 'Volume',
                data: volData,
                backgroundColor: colors,
                borderWidth: 0,
            }],
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: { legend: { display: false } },
            scales: {
                x: {
                    type: 'time',
                    time: { unit: 'minute' },
                    ticks: { display: false },
                    grid: { display: false },
                },
                y: {
                    position: 'right',
                    ticks: { color: '#8b949e', maxTicksLimit: 3 },
                    grid: { color: '#21262d' },
                },
            },
        },
    });
}

// ── Helpers ──────────────────────────────────────────────────────
function formatPrice(symbol, price) {
    if (symbol === 'BTC' || symbol === 'ETH') {
        return price.toLocaleString('en-US', { style: 'currency', currency: 'USD', minimumFractionDigits: 2 });
    }
    return '$' + price.toFixed(2);
}

// ── Init ─────────────────────────────────────────────────────────
startClock();
connectWebSocket();
