package com.shivam.exchange.order.service;

import com.shivam.exchange.engine.Order;
import com.shivam.exchange.engine.OrderBook;
import com.shivam.exchange.engine.OrderStatus;
import com.shivam.exchange.engine.OrderType;
import com.shivam.exchange.engine.Side;
import com.shivam.exchange.engine.Trade;
import com.shivam.exchange.order.dto.BookTopResponse;
import com.shivam.exchange.order.dto.OrderResponse;
import com.shivam.exchange.order.dto.PlaceOrderRequest;
import com.shivam.exchange.order.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
 * - Reserves cash (BUY) or shares (SELL) before an order reaches the book,
 *   and releases them when the unfilled part is cancelled.
 *
 * NOTE (Step 3): money/shares reserved for the FILLED part of an order stay reserved for now.
 * Step 4 (settlement) moves them from buyer to seller when a trade happens.
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
    private final Map<Long, Order> ordersById = new ConcurrentHashMap<>();
    private final AtomicLong nextOrderId = new AtomicLong(1);
    private final AccountService accountService;

    // Spring injects AccountService automatically (constructor injection).
    public ExchangeService(AccountService accountService) {
        this.accountService = accountService;
        for (String symbol : SYMBOLS) {
            books.put(symbol, new OrderBook(symbol));
        }
    }

    /**
     * @Transactional: the reservation and everything else in this method is one database
     * transaction. If anything fails (e.g. not enough cash), the whole thing is rolled back.
     */
    @Transactional
    public OrderResponse placeOrder(PlaceOrderRequest request) {git add -A
        accountService.requireAccount(request.ownerId());   // no account -> 404, no trading
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

        // A MARKET BUY has no price, so we can't know how much cash to lock up front.
        // (Real exchanges use a "price band"; we may add one later.) For now: LIMIT only.
        if (order.side() == Side.BUY && order.type() == OrderType.MARKET) {
            throw new IllegalArgumentException(
                    "MARKET BUY is not supported yet (cost unknown up front). Use a LIMIT order.");
        }

        synchronized (book) {
            reserveFor(order, book.symbol());          // not enough cash/shares -> 400, order never reaches the book

            List<Trade> trades = book.process(order);
            symbolByOrderId.put(orderId, book.symbol());
            ordersById.put(orderId, order);

            // The engine cancels the unfilled part of a MARKET order, or the whole order
            // on self-trade. That part will never trade, so unlock it now.
            if (order.status() == OrderStatus.CANCELLED) {
                releaseRemaining(order, book.symbol());
            }

            // Build the response INSIDE the lock: once we leave, another request could
            // match against this order and change its status/remainingQty.
            return OrderResponse.from(order, book.symbol(), trades);
        }
    }

    @Transactional
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
            releaseRemaining(ordersById.get(orderId), symbol);   // unlock what was reserved for the rest
        }
    }

    /** BUY: lock price x quantity cash.  SELL: lock quantity shares. */
    private void reserveFor(Order order, String symbol) {
        if (order.side() == Side.BUY) {
            accountService.reserveCash(order.ownerId(), cost(order.price(), order.quantity()));
        } else {
            accountService.reserveShares(order.ownerId(), symbol, order.quantity());
        }
    }

    /** Unlock what is still reserved for the part of the order that did not trade. */
    private void releaseRemaining(Order order, String symbol) {
        long remaining = order.remainingQty();
        if (remaining == 0) {
            return;
        }
        if (order.side() == Side.BUY) {
            accountService.releaseCash(order.ownerId(), cost(order.price(), remaining));
        } else {
            accountService.releaseShares(order.ownerId(), symbol, remaining);
        }
    }

    /** price x quantity, refusing absurd orders whose value would overflow a long. */
    private static long cost(long price, long quantity) {
        try {
            return Math.multiplyExact(price, quantity);
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("Order value is too large");
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
