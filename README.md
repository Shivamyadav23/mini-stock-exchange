# Mini Stock Exchange

A simplified stock exchange with a price-time priority **order matching engine** and a **paper-trading platform**, built in Java and deployed with a full DevOps stack.

See [DESIGN.md](DESIGN.md) for the full design.

## Status

| Week | Module | Status |
|---|---|---|
| 1 | `matching-engine` (pure Java) | 🚧 In progress |
| 2 | Spring Boot API, accounts, settlement | ⏳ |
| 3 | Market data, WebSocket, market-maker bot | ⏳ |
| 4–5 | Split into services over Kafka | ⏳ |
| 6–8 | Docker, Kubernetes, Helm, Terraform (AWS EKS) | ⏳ |
| 9–10 | GitHub Actions, ArgoCD, Prometheus, Grafana | ⏳ |

## Week 1: matching engine

### Requirements
- Java 21 (`JAVA_HOME` set)
- Maven is optional: the Maven Wrapper downloads Maven 3.9.9 on first run

### Run the tests
```bash
cd matching-engine
./mvnw test        # macOS / Linux / Git Bash
.\mvnw.cmd test    # Windows PowerShell / cmd
```

### Your task
Open `matching-engine/src/main/java/com/shivam/exchange/engine/OrderBook.java` and complete:
1. `crosses(...)` — does the incoming price reach the resting price?
2. `process(...)` — the matching loop (step-by-step hints are in the code).

Make the tests pass level by level (they are grouped in `OrderBookTest.java`):

| Level | Rule |
|---|---|
| 1 | Orders that don't match wait in the book |
| 2 | Basic matching; trade happens at the waiting order's price |
| 3 | Partial fills; sweeping several price levels |
| 4 | Time priority: same price, first come first served |
| 5 | Market orders: any price, never wait |
| 6 | Self-trade prevention and cancel |

### Code map
| File | What it is |
|---|---|
| `Order` | One order. Prices are `long` paise (₹100.50 = `10050`) |
| `Trade` | One deal between a buyer and a seller |
| `PriceLevel` | All orders waiting at one price, in arrival order |
| `OrderBook` | Bids and asks for one stock, plus the matching logic |
