# Mini Stock Exchange

A simplified stock exchange with a price-time priority **order matching engine** and a **paper-trading platform**, built in Java / Spring Boot and deployed with a full DevOps stack.

See [DESIGN.md](DESIGN.md) for the full design.

## Status

| Week | Module | Status |
|---|---|---|
| 1 | `matching-engine`: price-time priority matching (pure Java) | ✅ Done |
| 2 | `order-service`: REST API, accounts, reservations, settlement (Spring Boot + JPA) | ✅ Done |
| 3 | Market data, WebSocket, market-maker bot, JWT login | ⏳ |
| 4–5 | Split into services over Kafka | ⏳ |
| 6–8 | Docker, PostgreSQL, Kubernetes, Helm, Terraform (AWS EKS) | ⏳ |
| 9–10 | GitHub Actions CD, ArgoCD, Prometheus, Grafana | ⏳ |

**60 tests** across both modules (13 engine, 47 order-service).

## Features

- **Matching engine**: LIMIT and MARKET orders, price-time priority, partial fills, maker price, self-trade prevention, cancel
- **Accounts**: every new user gets ₹1,00,000 paper cash and 100 shares of each stock
- **Reservations**: placing an order locks the cash (BUY) or shares (SELL), so they can't be spent twice; cancelling unlocks them
- **Settlement**: every trade moves cash buyer → seller and shares seller → buyer in one database transaction; total cash in the system never changes
- **Safe money**: all amounts are `long` paise (₹100.50 = `10050`), overflow-checked; optimistic locking (`@Version`) on accounts and positions
- **Clean errors**: RFC 9457 problem-details JSON (400 / 404 / 409)

## Requirements
- Java 21 (`JAVA_HOME` set)
- Maven is optional: the Maven Wrapper downloads Maven 3.9.9 on first run

## Build and test
From the project root:
```bash
./mvnw test        # macOS / Linux / Git Bash
.\mvnw.cmd test    # Windows PowerShell / cmd
```

## Run the API
```bash
./mvnw install -DskipTests                 # build the engine jar that order-service depends on
./mvnw -pl order-service spring-boot:run
```
Or in IntelliJ: open `OrderServiceApplication` and click ▶.

| URL | What |
|---|---|
| http://localhost:8080/actuator/health | Health check |
| http://localhost:8080/h2-console | Browse the database (JDBC URL `jdbc:h2:file:./data/exchange`, user `sa`, empty password) |

The H2 database file lives in `data/` relative to where the app is started (git-ignored). Tests use a separate in-memory database.

## API

Prices are in **paise** (`10000` = ₹100.00). Symbols: `INFY`, `TCS`, `RELIANCE`, `HDFCBANK`, `SBIN`.

| Method | Path | What it does |
|---|---|---|
| POST | `/api/accounts` | Open an account `{"ownerId":"shivam"}` → 201 (409 if it exists) |
| GET | `/api/accounts/{ownerId}` | Cash and holdings |
| POST | `/api/orders` | Place an order (reserves cash/shares, matches, settles) → 201 |
| DELETE | `/api/orders/{orderId}` | Cancel an open order and release its reservation |
| GET | `/api/market/{symbol}/top` | Best bid and best ask |
| GET | `/api/market/symbols` | Tradable stocks |

### Try it (Git Bash)
```bash
# 1. Open two accounts
curl -s -X POST localhost:8080/api/accounts -H "Content-Type: application/json" -d '{"ownerId":"rahul"}'
curl -s -X POST localhost:8080/api/accounts -H "Content-Type: application/json" -d '{"ownerId":"shivam"}'

# 2. Rahul sells 5 INFY at ₹98 (5 shares are locked)
curl -s -X POST localhost:8080/api/orders -H "Content-Type: application/json" \
  -d '{"ownerId":"rahul","symbol":"INFY","side":"SELL","type":"LIMIT","price":9800,"quantity":5}'

# 3. Shivam buys 5 INFY at up to ₹100 → trade at ₹98 (the resting order's price)
curl -s -X POST localhost:8080/api/orders -H "Content-Type: application/json" \
  -d '{"ownerId":"shivam","symbol":"INFY","side":"BUY","type":"LIMIT","price":10000,"quantity":5}'

# 4. ₹490 moved from Shivam to Rahul, 5 INFY moved from Rahul to Shivam
curl -s localhost:8080/api/accounts/shivam    # cash 9951000, INFY 105
curl -s localhost:8080/api/accounts/rahul     # cash 10049000, INFY 95
```

## Code map

### `matching-engine` (`com.shivam.exchange.engine`)
| File | What it is |
|---|---|
| `Order` | One order: side, type, price (paise), quantity, remaining, status |
| `Trade` | One deal: buyer, seller, maker, taker, price (maker's), quantity |
| `PriceLevel` | All orders waiting at one price, first in first out |
| `OrderBook` | Bids and asks (`TreeMap`, best price first) plus the matching logic |

### `order-service` (`com.shivam.exchange.order`)
| Package | Files | What it does |
|---|---|---|
| `controller` | `OrderController`, `AccountController` | HTTP endpoints, thin: validate, call service, return |
| `service` | `ExchangeService` | One `OrderBook` per stock, order ids, thread safety, reserve → match → settle |
| | `AccountService` | Opens accounts, loads rows, calls entity rules, settles trades (`@Transactional`) |
| `entity` | `Account`, `Position` | Database tables + money/share rules (reserve, release, settle) |
| `repository` | `AccountRepository`, `PositionRepository` | Spring Data JPA, no SQL written by hand |
| `dto` | `*Request`, `*Response` | JSON shapes in and out |
| `exception` | `ApiExceptionHandler` + 3 exceptions | Errors → 400 / 404 / 409 problem details |

## Tech
Java 21 · Spring Boot 3.5 · Spring Data JPA / Hibernate · H2 · JUnit 5 · AssertJ · Maven (multi-module) · GitHub Actions
