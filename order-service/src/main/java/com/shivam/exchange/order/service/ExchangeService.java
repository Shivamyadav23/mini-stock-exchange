package com.shivam.exchange.order.service;

import com.shivam.exchange.engine.Order;
import com.shivam.exchange.engine.OrderBook;
import com.shivam.exchange.engine.Side;
import com.shivam.exchange.engine.Trade;
import com.shivam.exchange.order.api.dto.BookTopResponse;
import com.shivam.exchange.order.api.dto.OrderResponse;
import com.shivam.exchange.order.api.dto.PlaceOrderRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The bridge between the web layer and your Week 1 engine.
 *
 * - Keeps one OrderBook per stock.
 * - Gives every new order a unique id.
 * - Remembers which stock each order belongs to (needed for cancel).
 *
 * THREAD SAFETY: a web server handles many requests at the same time, but OrderBook is NOT
 * thread-safe. So every access to a book is inside synchronized (book) { ... }: only one
 * request at a time can touch a given stock's book. Different stocks still run in parallel.
 * (In Week 4-5 Kafka replaces this lock: one consumer thread per stock = "single writer".)
 */
@Service
public class ExchangeService {

    public static final List<String> SYMBOLS = List.of("INFY", "TCS", "RELIANCE", "HDFCBANK", "SBIN");

    private final Map<String, OrderBook> books = new ConcurrentHashMap<>();
    private final Map<Long, String> symbolByOrderId = new ConcurrentHashMap<>();
    private final AtomicLong nextOrderId = new AtomicLong(1);

    public ExchangeService() {
        for (String symbol : SYMBOLS) {
            books.put(symbol, new OrderBook(symbol));
        }
    }

    public OrderResponse placeOrder(PlaceOrderRequest request) {
        OrderBook book = bookFor(request.symbol());
        long orderId = nextOrderId.getAndIncrement();

        Order order = switch (request.type()) {
            case LIMIT -> {
                if (request.price() == null) {
                    throw new IllegalArgumentException("price is required for LIMIT orders");
                }
                yield Order.limit(orderId, request.ownerId(), request.side(), request.price(), request.quantity());
            }
            case MARKET -> Order.market(orderId, request.ownerId(), request.side(), request.quantity());
        };

        synchronized (book) {
            List<Trade> trades = book.process(order);
            symbolByOrderId.put(orderId, book.symbol());
            // Build the response INSIDE the lock: once we leave, another request could
            // match against this order and change its status/remainingQty.
            return OrderResponse.from(order, book.symbol(), trades);
        }
    }

    public void cancel(long orderId) {
        String symbol = symbolByOrderId.get(orderId);
        if (symbol == null) {
            throw new NotFoundException("Order " + orderId + " does not exist");
        }
        OrderBook book = books.get(symbol);
        synchronized (book) {
            if (!book.cancel(orderId)) {
                throw new NotFoundException("Order " + orderId + " is not open (already filled or cancelled)");
            }
        }
    }

    public BookTopResponse topOfBook(String symbol) {
        OrderBook book = bookFor(symbol);
        synchronized (book) {
            Long bestBid = book.bestBid();
            Long bestAsk = book.bestAsk();
            return new BookTopResponse(
                    book.symbol(),
                    bestBid,
                    bestBid == null ? 0 : book.quantityAt(Side.BUY, bestBid),
                    bestAsk,
                    bestAsk == null ? 0 : book.quantityAt(Side.SELL, bestAsk));
        }
    }

    private OrderBook bookFor(String symbol) {
        OrderBook book = books.get(symbol.toUpperCase(Locale.ROOT));
        if (book == null) {
            throw new NotFoundException("Unknown symbol " + symbol + ". Valid symbols: " + SYMBOLS);
        }
        return book;
    }
}
