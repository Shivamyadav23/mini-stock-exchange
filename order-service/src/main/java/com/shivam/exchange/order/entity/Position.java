package com.shivam.exchange.order.entity;

import com.shivam.exchange.order.exception.InsufficientBalanceException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

/**
 * How many shares of ONE stock ONE user owns. Becomes the "positions" table:
 *
 *   id | owner_id | symbol | quantity | quantity_reserved
 *    1 | shivam   | INFY   |      100 |                 0
 *    2 | shivam   | TCS    |      100 |                 0
 *
 *   quantity         = total shares owned
 *   quantityReserved = shares locked by open SELL orders (used in Step 3)
 *   available        = quantity - quantityReserved  (what the user can still sell)
 *
 * The unique constraint means one user can have only ONE row per stock.
 */
@Entity
@Table(
        name = "positions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"owner_id", "symbol"}))
public class Position {

    /** Primary key: a number the database generates automatically (1, 2, 3 ...). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private String ownerId;

    @Column(nullable = false)
    private String symbol;

    private long quantity;

    private long quantityReserved;

    /** Optimistic locking, same idea as in Account. */
    @Version
    private Long version;

    /** JPA needs an empty constructor to create objects when it reads rows from the database. */
    protected Position() {
    }

    public Position(String ownerId, String symbol, long quantity) {
        this.ownerId = ownerId;
        this.symbol = symbol;
        this.quantity = quantity;
        this.quantityReserved = 0;       // a new position has nothing locked yet
    }

    /** Shares the user can still sell = owned minus what is locked by open sell orders. */
    public long availableQuantity() {
        return quantity - quantityReserved;
    }

    /**
     * Lock shares for a new SELL order, so the same shares can't be sold twice.
     *
     * Example: quantity 100, reserved 0, reserveShares(30)
     *          -> reserved becomes 30, available becomes 70
     *
     * Rules: same idea as Account.reserveCash
     *   1. qty must be greater than 0             -> otherwise throw IllegalArgumentException
     *   2. qty must be <= availableQuantity()     -> otherwise throw InsufficientBalanceException
     *   3. if both are OK                         -> add qty to quantityReserved
     */
    public void reserveShares(long qty) {
        if (qty <= 0) {
            throw new IllegalArgumentException("qty must be positive, got " + qty);
        }
        if (qty > availableQuantity()) {
            throw new InsufficientBalanceException(
                    "Not enough " + symbol + " shares: need " + qty + ", available " + availableQuantity());
        }
        quantityReserved += qty;
    }

    /**
     * Unlock shares when a SELL order is cancelled.
     *
     * Rules: same idea as Account.releaseCash
     *   1. qty must be greater than 0             -> otherwise throw IllegalArgumentException
     *   2. qty must be <= quantityReserved        -> otherwise throw IllegalArgumentException
     *   3. if both are OK                         -> subtract qty from quantityReserved
     */
    public void releaseShares(long qty) {
        if (qty <= 0) {
            throw new IllegalArgumentException("qty must be positive, got " + qty);
        }
        if (qty > quantityReserved) {
            throw new IllegalArgumentException(
                    "Cannot release " + qty + " shares: only " + quantityReserved + " are reserved");
        }
        quantityReserved -= qty;
    }

    // ============================== Step 4: settlement ==============================

    /**
     * SELLER side of a trade: the sold shares leave, and their lock is removed.
     *
     * Example: Rahul has 100 INFY, 5 reserved for his SELL order. 5 are sold.
     *   deliverShares(5) -> quantity 100 -> 95, quantityReserved 5 -> 0
     *
     * Rules:
     *   1. qty must be > 0                  -> otherwise IllegalArgumentException
     *   2. qty must be <= quantityReserved  -> otherwise IllegalArgumentException
     *                                          (you can only deliver shares that were locked for selling)
     *   3. quantity         -= qty
     *   4. quantityReserved -= qty
     */
    public void deliverShares(long qty) {
        if (qty <= 0) {
            throw new IllegalArgumentException("qty must be positive, got " + qty);
        }
        if (qty > quantityReserved) {
            throw new IllegalArgumentException("only locked shares can be delivered");
        }
        quantityReserved -= qty;
        quantity -= qty;

    }

    /**
     * BUYER side of a trade: receive the shares.
     *
     * Example: Shivam bought 5 INFY -> receiveShares(5) -> quantity 100 -> 105
     *
     * Rules:
     *   1. qty must be > 0   -> otherwise IllegalArgumentException
     *   2. quantity += qty
     */
    public void receiveShares(long qty) {
        if (qty <= 0) {
            throw new IllegalArgumentException("qty must be positive, got " + qty);
        }
        quantity += qty;

    }

    public Long getId() {
        return id;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public String getSymbol() {
        return symbol;
    }

    public long getQuantity() {
        return quantity;
    }

    public long getQuantityReserved() {
        return quantityReserved;
    }

    public Long getVersion() {
        return version;
    }
}
