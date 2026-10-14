package com.shivam.exchange.engine;

/**
 * One order sent by a user.
 *
 * Prices are in PAISE as a long (Rs 100.50 -> 10050). Never use double for money:
 * 0.1 + 0.2 != 0.3 in floating point, and an exchange must never lose a paisa.
 *
 * id, ownerId, side, type, price and quantity never change.
 * remainingQty and status change as the order gets filled.
 */
public final class Order {

    private final long id;
    private final String ownerId;
    private final Side side;
    private final OrderType type;
    private final long price;       // paise; 0 for MARKET orders
    private final long quantity;

    private long remainingQty;
    private OrderStatus status = OrderStatus.NEW;

    private Order(long id, String ownerId, Side side, OrderType type, long price, long quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be > 0");
        }
        if (type == OrderType.LIMIT && price <= 0) {
            throw new IllegalArgumentException("limit price must be > 0");
        }
        this.id = id;
        this.ownerId = ownerId;
        this.side = side;
        this.type = type;
        this.price = price;
        this.quantity = quantity;
        this.remainingQty = quantity;
    }

    public static Order limit(long id, String ownerId, Side side, long price, long quantity) {
        return new Order(id, ownerId, side, OrderType.LIMIT, price, quantity);
    }

    public static Order market(long id, String ownerId, Side side, long quantity) {
        return new Order(id, ownerId, side, OrderType.MARKET, 0, quantity);
    }

    /** Reduce remaining quantity after a trade and update the status. */
    void fill(long qty) {
        if (qty <= 0 || qty > remainingQty) {
            throw new IllegalArgumentException("invalid fill " + qty + " for remaining " + remainingQty);
        }
        remainingQty -= qty;
        status = remainingQty == 0 ? OrderStatus.FILLED : OrderStatus.PARTIALLY_FILLED;
    }

    void cancel() {
        status = OrderStatus.CANCELLED;
    }

    public boolean isFilled() {
        return remainingQty == 0;
    }

    public long id() { return id; }
    public String ownerId() { return ownerId; }
    public Side side() { return side; }
    public OrderType type() { return type; }
    public long price() { return price; }
    public long quantity() { return quantity; }
    public long remainingQty() { return remainingQty; }
    public OrderStatus status() { return status; }

    @Override
    public String toString() {
        return "Order{id=%d, owner=%s, %s %s %d @ %d, remaining=%d, %s}"
                .formatted(id, ownerId, side, type, quantity, price, remainingQty, status);
    }
}
