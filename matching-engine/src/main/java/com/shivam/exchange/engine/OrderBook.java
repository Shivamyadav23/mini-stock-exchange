package com.shivam.exchange.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * The order book for ONE stock (e.g. INFY).
 *
 * bids = buyers, highest price first.
 * asks = sellers, lowest price first.
 * So bids.firstEntry() is always the BEST buyer and asks.firstEntry() is always the BEST seller.
 */
public final class OrderBook {

    private final String symbol;
    private final NavigableMap<Long, PriceLevel> bids = new TreeMap<>(Collections.reverseOrder());
    private final NavigableMap<Long, PriceLevel> asks = new TreeMap<>();
    private final Map<Long, Order> restingOrders = new HashMap<>();

    public OrderBook(String symbol) {
        this.symbol = symbol;
    }

    /**
     * Process one incoming order: match it against the other side, then
     * rest (LIMIT) or cancel (MARKET) whatever is left.
     *
     * @return the trades that happened, in the order they happened
     */

    public List<Trade> process(Order incoming) {
        if (restingOrders.containsKey(incoming.id())) {
            throw new IllegalArgumentException("duplicate order id " + incoming.id());
        }
        List<Trade> trades = new ArrayList<>();

        // Buyers match against sellers, sellers match against buyers.
        NavigableMap<Long, PriceLevel> opposite = incoming.side() == Side.BUY ? asks : bids;

        // STEP 1: Keep matching while I still need shares AND there are orders on the other side.
        //         (Mango story: "while I still need mangoes and there are sellers")
        while (!opposite.isEmpty() && !incoming.isFilled()) {

            // a) The best price line on the other side (cheapest seller / highest buyer)
            PriceLevel best = opposite.firstEntry().getValue();

            // b) Is that price OK for me? If not, nobody further down the list is OK either -> stop.
            if (!crosses(incoming, best.price())) {
                break;
            }

            // c) The person who has waited longest at that price (front of the line)
            Order resting = best.peekFirst();

            // d) Never trade with yourself: cancel my order and stop.
            if (resting.ownerId().equals(incoming.ownerId())) {
                incoming.cancel();
                return trades;
            }

            // e) How many shares in this deal? The smaller of "what I need" and "what they have".
            long qty = Math.min(incoming.remainingQty(), resting.remainingQty());

            // f) Write the receipt. Price = the waiting (resting) order's price.
            trades.add(newTrade(incoming, resting, qty));

            // g) Reduce both orders, and the total shown at this price line.
            incoming.fill(qty);
            resting.fill(qty);
            best.reduceTotal(qty);

            // h) If the waiting order is finished, remove it from the line and from the id map.
            if (resting.isFilled()) {
                best.pollFirst();
                restingOrders.remove(resting.id());
            }

            // i) If nobody is left at this price, remove the whole price line.
            if (best.isEmpty()) {
                opposite.remove(best.price());
            }
        }

        // STEP 2: Loop is over. If I still need shares:
        //         LIMIT  -> wait in the book for a future match
        //         MARKET -> cancel the rest (market orders never wait)
        if (!incoming.isFilled()) {
            if (incoming.type() == OrderType.LIMIT) {
                rest(incoming);
            } else {
                incoming.cancel();
            }
        }

        return trades;
    }

    /**
     * Does the incoming order's price reach this resting price?
     *
     * Examples:
     *   MARKET order                      -> always true (any price is OK)
     *   BUY  limit 100 vs resting ask  98 -> true  (I pay up to 100, they want 98)
     *   BUY  limit 100 vs resting ask 102 -> false
     *   SELL limit  98 vs resting bid 100 -> true  (I accept down to 98, they pay 100)
     *   SELL limit  98 vs resting bid  95 -> false
     */
    private boolean crosses(Order incoming, long restingPrice) {
        if (incoming.type() == OrderType.MARKET) {
            return true;
        }
        if (incoming.side() == Side.BUY) {
            if (incoming.price() >= restingPrice) {
                return true;
            } else {
                return false;
            }
        } else {
            if (incoming.price() <= restingPrice) {
                return true;
            } else {
                return false;
            }

        }
    }
    
    
    
    

    private Trade newTrade(Order taker, Order maker, long qty) {
        long buyId = taker.side() == Side.BUY ? taker.id() : maker.id();
        long sellId = taker.side() == Side.SELL ? taker.id() : maker.id();
        return new Trade(buyId, sellId, maker.id(), taker.id(), maker.price(), qty);
    }

    /** Put an order in the book to wait. Given to you; you do not need to change it. */
    private void rest(Order order) {
        NavigableMap<Long, PriceLevel> side = order.side() == Side.BUY ? bids : asks;
        side.computeIfAbsent(order.price(), PriceLevel::new).add(order);
        restingOrders.put(order.id(), order);
    }

    /** Cancel a waiting order. Returns false if it is not in the book (already filled or unknown). */
    public boolean cancel(long orderId) {
        Order order = restingOrders.remove(orderId);
        if (order == null) {
            return false;
        }
        NavigableMap<Long, PriceLevel> side = order.side() == Side.BUY ? bids : asks;
        PriceLevel level = side.get(order.price());
        level.remove(order);
        if (level.isEmpty()) {
            side.remove(order.price());
        }
        order.cancel();
        return true;
    }

    // ---------- read-only helpers (used by tests, later by market data) ----------

    public Long bestBid() {
        return bids.isEmpty() ? null : bids.firstKey();
    }

    public Long bestAsk() {
        return asks.isEmpty() ? null : asks.firstKey();
    }

    /** Total waiting quantity at a price on one side (0 if none). */
    public long quantityAt(Side side, long price) {
        PriceLevel level = (side == Side.BUY ? bids : asks).get(price);
        return level == null ? 0 : level.totalQty();
    }

    public boolean isResting(long orderId) {
        return restingOrders.containsKey(orderId);
    }

    public String symbol() {
        return symbol;
    }
}
