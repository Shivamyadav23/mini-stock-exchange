# Mini Stock Exchange

A simplified stock exchange with a price-time priority **order matching engine** and a **paper-trading platform**, built in Java and deployed with a full DevOps stack.

See [DESIGN.md](DESIGN.md) for the full design.

## Status

| Week | Module | Status |
|---|---|---|
| 1 | `matching-engine` (pure Java) | ✅ Done (13 tests) |
| 2 | `order-service`: Spring Boot API, accounts, settlement | 🚧 In progress (Day 1: REST API) |
| 3 | Market data, WebSocket, market-maker bot | ⏳ |
| 4–5 | Split into services over Kafka | ⏳ |
| 6–8 | Docker, Kubernetes, Helm, Terraform (AWS EKS) | ⏳ |
| 9–10 | GitHub Actions, ArgoCD, Prometheus, Grafana | ⏳ |

## Requirements
- Java 21 (`JAVA_HOME` set)
- Maven is optional: the Maven Wrapper downloads Maven 3.9.9 on first run

## Build and test everything
From the project root:
```bash
./mvnw test        # macOS / Linux / Git Bash
.\mvnw.cmd test    # Windows PowerShell / cmd
```

## Run the API
```bash
./mvnw install -DskipTests          # build the engine jar so order-service can use it
./mvnw -pl order-service spring-boot:run
```
Or in IntelliJ: open `OrderServiceApplication` and click the green ▶ button.

Health check: http://localhost:8080/actuator/health

## API

Prices are in **paise** (`10000` = ₹100.00). Symbols: `INFY`, `TCS`, `RELIANCE`, `HDFCBANK`, `SBIN`.

| Method | Path | What it does |
|---|---|---|
| POST | `/api/orders` | Place an order |
| DELETE | `/api/orders/{orderId}` | Cancel an open order |
| GET | `/api/market/{symbol}/top` | Best bid and best ask |
| GET | `/api/market/symbols` | List of tradable stocks |

### Try it (Git Bash)
```bash
# Rahul sells 5 INFY at Rs 98
curl -s -X POST localhost:8080/api/orders -H "Content-Type: application/json" \
  -d '{"ownerId":"rahul","symbol":"INFY","side":"SELL","type":"LIMIT","price":9800,"quantity":5}'

# Shivam buys 5 INFY at up to Rs 100 -> trade at Rs 98
curl -s -X POST localhost:8080/api/orders -H "Content-Type: application/json" \
  -d '{"ownerId":"shivam","symbol":"INFY","side":"BUY","type":"LIMIT","price":10000,"quantity":5}'

# Best bid / ask
curl -s localhost:8080/api/market/INFY/top
```

## Code map

### `matching-engine` (Week 1)
| File | What it is |
|---|---|
| `Order` | One order. Prices are `long` paise (₹100.50 = `10050`) |
| `Trade` | One deal between a buyer and a seller |
| `PriceLevel` | All orders waiting at one price, in arrival order |
| `OrderBook` | Bids and asks for one stock, plus the matching logic |

### `order-service` (Week 2)
| File | What it is |
|---|---|
| `OrderServiceApplication` | Starts the Spring Boot app |
| `api/OrderController` | HTTP endpoints (thin: read request, call service) |
| `api/ApiExceptionHandler` | Turns exceptions into 400 / 404 JSON errors |
| `api/dto/*` | Request and response JSON shapes |
| `service/ExchangeService` | One `OrderBook` per stock, order ids, thread safety |
