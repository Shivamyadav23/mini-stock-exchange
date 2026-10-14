# Mini Stock Exchange — Design Document

**Author:** Shivam Yadav
**Status:** Draft v1
**Stack:** Java 21, Spring Boot 3, PostgreSQL, Redis, Kafka, Docker, Kubernetes (EKS), Terraform, GitHub Actions, ArgoCD, Prometheus, Grafana, OpenTelemetry

---

## 1. Overview

A simplified exchange with a **price-time priority order matching engine** and a **paper-trading platform** on top of it. Users get virtual cash, place orders on a handful of instruments, get matched against each other (and a market-maker bot), and see live market data.

The project exists to prove two things on one resume line:
1. **Backend engineering** — correct, tested, concurrent Java with real domain logic.
2. **Platform engineering** — the same system deployed on Kubernetes via Terraform, with CI/CD and full observability.

### Goals (v1)
- Limit and market orders, cancel, partial fills.
- Price-time priority matching, one order book per symbol.
- Paper-trading accounts: virtual cash, funds reservation, positions, P&L.
- Market data: trades, top-of-book (L1), 1-minute candles, live via WebSocket.
- Every state change published as an event on Kafka.
- Deterministic, unit-tested matching engine.

### Non-goals (v1)
- Real money, real exchange connectivity, regulatory compliance.
- Short selling, margin, derivatives, stop orders, IOC/FOK (candidates for v2).
- Multi-region or exchange-grade latency (microseconds). Target is low milliseconds.

---

## 2. Domain rules

| Rule | Decision |
|---|---|
| Instruments | 5 fixed symbols seeded at startup (e.g. `INFY`, `TCS`, `RELIANCE`, `HDFCBANK`, `SBIN`) |
| Tick size | ₹0.05 |
| Price representation | `long` in **paise** (never `double`) — ₹1,234.55 → `123455` |
| Quantity | `long`, lot size 1 |
| Order types | `LIMIT`, `MARKET` |
| Sides | `BUY`, `SELL` |
| Priority | Best price first; at equal price, earliest order first (FIFO) |
| Trade price | The **resting** (maker) order's price |
| Market order with no liquidity | Unfilled remainder is **cancelled** (no resting market orders) |
| Self-trade | Prevented: incoming order cancels against own resting order (v1: reject the incoming remainder) |
| Buy funds check | Reserve `price × qty` (market orders: reserve against a price band, e.g. last price + 5%) |
| Sell check | Must hold enough free position quantity (no shorting) |

### Order lifecycle

```
NEW ──► PARTIALLY_FILLED ──► FILLED
 │            │
 └────────────┴──► CANCELLED
REJECTED (failed validation/risk, never reaches the book)
```

---

## 3. Architecture

### Phase 1 (Weeks 1–3): modular monolith
One Spring Boot app with clean module boundaries. Easier to get correct and test; module boundaries become service boundaries later.

### Phase 2 (Weeks 4–5): split into services over Kafka

```
                ┌──────────────────┐
  REST/WS ─────►│  order-service   │  validation, risk, funds reservation
  clients       │  (API + accounts)│──────────┐
                └────────┬─────────┘          │
                         │ orders.commands    │ PostgreSQL
                         ▼  (key = symbol)    │ (accounts, orders,
                ┌──────────────────┐          │  trades, positions)
                │ matching-engine  │          │
                │ in-memory books  │          │
                └────────┬─────────┘          │
                         │ exchange.events    │
           ┌─────────────┼───────────────┐    │
           ▼             ▼               ▼    │
   ┌──────────────┐ ┌──────────────┐ ┌───────┴──────┐
   │ market-data  │ │  settlement  │ │ market-maker │
   │ L1, candles, │ │ (in order-   │ │  bot         │
   │ WebSocket    │ │  service)    │ │              │
   └──────┬───────┘ └──────────────┘ └──────────────┘
          │ Redis (L1 cache, latest candles)
```

| Service | Responsibility | State |
|---|---|---|
| **order-service** | REST API, auth (JWT), validation, risk checks, funds/position reservation, settlement of trades into accounts | PostgreSQL |
| **matching-engine** | Order books, matching, emits accepted/fill/cancel events | In memory; rebuilt from Kafka on restart |
| **market-data-service** | Consumes trades and book events, builds L1 and 1-min candles, pushes over WebSocket | Redis (+ candles to PostgreSQL) |
| **market-maker-bot** | Keeps both sides quoted around a reference price so the book is never empty | Stateless |

### Why one Kafka partition key per symbol
`orders.commands` is keyed by **symbol**, so all orders for a symbol land on one partition and are consumed by one engine thread **in order**. That gives a **single writer per order book**: no locks inside the matching logic, deterministic results, and horizontal scaling by spreading symbols across partitions.

---

## 4. Matching engine design

### Order book data structure

```java
// Per symbol
TreeMap<Long, PriceLevel> bids;   // comparator: descending (best bid = firstKey)
TreeMap<Long, PriceLevel> asks;   // ascending (best ask = firstKey)
HashMap<Long, Order> ordersById;  // O(1) lookup for cancel

class PriceLevel {
    long price;
    long totalQty;
    ArrayDeque<Order> orders;     // FIFO = time priority
}
```

| Operation | Complexity |
|---|---|
| Add resting order | O(log P) where P = number of price levels |
| Best bid/ask | O(1) amortized (`firstEntry`) |
| Cancel | O(1) lookup + O(n) removal within the level (v2: intrusive doubly linked list for O(1)) |
| Match | O(fills + log P) |

### Matching algorithm (incoming BUY; SELL is symmetric)

```
while incoming.remaining > 0 and asks not empty:
    bestAsk = asks.firstEntry()
    if incoming is LIMIT and bestAsk.price > incoming.price: break
    resting = bestAsk.orders.peekFirst()
    if resting.owner == incoming.owner: reject remainder (self-trade); break
    fillQty = min(incoming.remaining, resting.remaining)
    emit Trade(price = resting.price, qty = fillQty, maker = resting, taker = incoming)
    decrement both; if resting fully filled -> pop it; if level empty -> remove level

if incoming.remaining > 0:
    if LIMIT  -> rest it in bids
    if MARKET -> cancel remainder
```

### Engine rules
- The engine is a **pure function of its input sequence**: same commands in → same events out. This is what makes it testable and recoverable.
- No database calls, no clocks, no randomness inside the engine. Timestamps and sequence numbers come in with the command.
- Each event carries a monotonically increasing `sequence` per symbol.

### Recovery
On restart, the engine replays `orders.commands` from the last snapshot offset. v1: replay from the beginning of a compacted window. v2: periodic book snapshots (to Redis or S3) + replay from the snapshot's offset.

---

## 5. Data model (PostgreSQL)

```sql
users        (id, username, password_hash, created_at)
accounts     (id, user_id, cash_balance, cash_reserved, updated_at)       -- paise
instruments  (symbol PK, name, tick_size, active)
orders       (id, user_id, symbol, side, type, price, qty, filled_qty,
              status, created_at, updated_at)
trades       (id, symbol, price, qty, buy_order_id, sell_order_id,
              sequence, executed_at)
positions    (user_id, symbol, qty, qty_reserved, avg_price, PK(user_id, symbol))
ledger       (id, account_id, amount, reason, ref_id, created_at)         -- every cash movement
candles_1m   (symbol, bucket_start, open, high, low, close, volume, PK(symbol, bucket_start))
```

**Invariants (tested):**
- `cash_balance >= cash_reserved >= 0`
- `positions.qty >= positions.qty_reserved >= 0`
- Sum of the `ledger` per account equals `cash_balance`.
- For every trade, buyer's cash decrease equals seller's cash increase.

Settlement of a trade updates both accounts, both positions and the ledger in **one transaction**, and is **idempotent** (keyed on `trade.id`) because Kafka delivers at least once.

---

## 6. APIs

### REST (order-service)

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/auth/register`, `/api/auth/login` | JWT auth |
| GET | `/api/account` | Cash, reserved, positions, P&L |
| POST | `/api/orders` | Place order `{symbol, side, type, price?, qty, clientOrderId}` |
| DELETE | `/api/orders/{id}` | Cancel |
| GET | `/api/orders?status=OPEN` | My orders |
| GET | `/api/trades?symbol=` | My trades |
| GET | `/api/market/{symbol}/book?depth=5` | Order book snapshot |
| GET | `/api/market/{symbol}/candles?from=&to=` | 1-min candles |

`clientOrderId` makes order placement **idempotent** (safe client retries).

### WebSocket (market-data-service)
`/ws/market` — subscribe to `l1.{symbol}`, `trades.{symbol}`, `candles.{symbol}`.

### Kafka topics

| Topic | Key | Payload |
|---|---|---|
| `orders.commands` | symbol | `NewOrder`, `CancelOrder` |
| `exchange.events` | symbol | `OrderAccepted`, `OrderRejected`, `Trade`, `OrderCancelled`, `BookUpdate` |
| `marketdata.candles` | symbol | `Candle1m` |

Payloads as JSON in v1; Avro or Protobuf with a schema registry is a v2 upgrade (good interview talking point).

---

## 7. Non-functional targets

| Metric | v1 target |
|---|---|
| Engine throughput | ≥ 50,000 orders/sec per symbol (in-process benchmark, JMH) |
| Order placement p99 (API → accepted) | < 50 ms |
| Recovery after engine restart | < 30 s |
| Correctness | Zero invariant violations under randomized load test |

---

## 8. Testing strategy

| Level | Tools | What |
|---|---|---|
| Unit | JUnit 5, AssertJ | Order book: every matching rule in §2 has a test |
| Property-based | jqwik | Random order streams → invariants hold (no crossed book, qty conserved) |
| Integration | Testcontainers (Postgres, Kafka, Redis) | Settlement idempotency, end-to-end order → trade → balances |
| Contract | Spring Cloud Contract (v2) | Event schemas between services |
| Performance | JMH, k6 | Engine throughput, API latency |

Rule: **no matching-engine code merges without tests.**

---

## 9. Platform and DevOps

| Area | Choice |
|---|---|
| Local dev | Docker Compose (Postgres, Kafka in KRaft mode, Redis, all services) |
| Containers | Multi-stage Dockerfiles, distroless or Temurin JRE base images |
| Orchestration | Kubernetes: kind locally, AWS EKS in cloud |
| Packaging | Helm chart per service |
| Infrastructure as Code | Terraform: VPC, EKS, RDS Postgres, ElastiCache Redis; Kafka via Strimzi on the cluster (cheaper than MSK) |
| CI | GitHub Actions: build, test (incl. Testcontainers), image build and push to ECR, Trivy scan |
| CD | ArgoCD (GitOps) watching a `deploy/` folder |
| Metrics | Micrometer → Prometheus → Grafana |
| Tracing | OpenTelemetry Java agent → Tempo or Jaeger |
| Logs | Structured JSON → Loki |
| Resilience | Resilience4j circuit breakers and timeouts on service calls |

**Key metrics to export:** orders received/rejected per second, match latency histogram, book depth per symbol, Kafka consumer lag, settlement failures, WebSocket connections.

**Cost control:** EKS is torn down with `terraform destroy` when not in use; everything also runs on kind for demos.

---

## 10. Repository structure

```
mini-stock-exchange/
├── DESIGN.md
├── README.md
├── docker-compose.yml
├── common/                  # shared event/command classes
├── matching-engine/         # pure Java engine + Kafka adapter
├── order-service/           # REST, auth, risk, settlement
├── market-data-service/     # candles, L1, WebSocket
├── market-maker-bot/
├── infra/
│   ├── terraform/           # AWS: VPC, EKS, RDS, ElastiCache
│   └── helm/                # one chart per service
├── deploy/                  # ArgoCD-watched manifests/values
├── observability/           # Grafana dashboards, alert rules
└── .github/workflows/
```

Phase 1 starts as a single Maven multi-module build with the engine as a plain Java module (no Spring) so it stays pure and fast.

---

## 11. Milestones

| Week | Deliverable | Done when |
|---|---|---|
| 1 | Matching engine core (pure Java) | All §2 rules covered by unit tests |
| 2 | Spring Boot API, auth, accounts, funds reservation, settlement (monolith) | End-to-end order → trade → balances works; Testcontainers tests pass |
| 3 | Market data: L1, candles, WebSocket; market-maker bot; simple UI or Postman collection | Live book visible while bot trades |
| 4–5 | Split into services over Kafka; idempotent settlement; Resilience4j | Docker Compose runs all services; engine restart recovers from Kafka |
| 6–8 | Dockerfiles, Helm, kind, then Terraform + EKS | `terraform apply` + `helm install` brings up the whole system |
| 9–10 | GitHub Actions + ArgoCD; Prometheus, Grafana, OpenTelemetry | A push to `main` deploys automatically; dashboards show live metrics |
| After | README with architecture diagram, benchmark results, demo GIF | Ready to link on resume and LinkedIn |

---

## 12. Interview talking points this design creates
- Why `long` paise instead of `double` for money.
- Why a single writer per order book beats locking, and how Kafka partitioning gives it.
- How the engine stays deterministic, and why that enables replay-based recovery.
- At-least-once delivery and idempotent settlement.
- `TreeMap` vs. array-indexed price levels, and the O(1) cancel upgrade.
- Trade-offs: Strimzi vs. MSK, monolith-first vs. microservices-first.

---

## 13. Open questions
1. UI: minimal React page, or Postman/WebSocket client only for v1?
2. Market-maker reference price: fixed seed prices, or seeded from real end-of-day NSE data?
3. Should v2 add IOC/FOK and stop orders, or prioritize snapshots and O(1) cancel?
