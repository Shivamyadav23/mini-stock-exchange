package com.shivam.exchange.engine;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * All orders waiting at ONE price, in arrival order (first in, first out).
 * This queue is what gives "time priority": whoever came first at this price gets filled first.
 */
final class PriceLevel {

    private final long price;
    private final Deque<Order> orders = new ArrayDeque<>();
    private long totalQty;

    PriceLevel(long price) {
        this.price = price;
    }

    void add(Order order) {
        orders.addLast(order);
        totalQty += order.remainingQty();
    }

    /** The oldest order at this price, without removing it. */
    Order peekFirst() {
        return orders.peekFirst();
    }

    /** Remove the oldest order (call this once it is fully filled). */
    Order pollFirst() {
        Order o = orders.pollFirst();
        return o;
    }

    /** Call after filling an order at this level so totalQty stays correct. */
    void reduceTotal(long qty) {
        totalQty -= qty;
    }

    boolean remove(Order order) {
        boolean removed = orders.remove(order);
        if (removed) {
            totalQty -= order.remainingQty();
        }
        return removed;
    }

    boolean isEmpty() {
        return orders.isEmpty();
    }

    long price() { return price; }
    long totalQty() { return totalQty; }
    int orderCount() { return orders.size(); }
}
