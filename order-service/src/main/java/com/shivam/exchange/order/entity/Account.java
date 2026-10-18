package com.shivam.exchange.order.entity;

import com.shivam.exchange.order.exception.InsufficientBalanceException;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * One user's cash account. This class becomes the "accounts" table in the database:
 * every Account object = one row, every field = one column.
 *
 * All money is in PAISE as long (Rs 1,00,000 = 10_000_000), never double.
 *
 *   cashBalance  = total money the user has
 *   cashReserved = money locked by open BUY orders (used in Step 3)
 *   available    = cashBalance - cashReserved  (what the user can still spend)
 */
@Entity
@Table(name = "accounts")
public class Account {

    /** Primary key: the user's name, e.g. "shivam". */
    @Id
    private String ownerId;

    private long cashBalance;

    private long cashReserved;

    /**
     * Optimistic locking: Hibernate adds 1 on every update. If two requests update the same
     * account at the same time, the second one fails instead of silently overwriting the first.
     * Capital-L Long on purpose: null tells Spring this is a NEW account (insert, not update).
     */
    @Version
    private Long version;

    /** JPA needs an empty constructor to create objects when it reads rows from the database. */
    protected Account() {
    }

    public Account(String ownerId, long startingCash) {
        this.ownerId = ownerId;          // "this.ownerId" = the field, "ownerId" = the parameter
        this.cashBalance = startingCash;
        this.cashReserved = 0;           // a new account has nothing locked yet
    }

    /** Money the user can still spend = balance minus what is locked by open buy orders. */
    public long availableCash() {
        return cashBalance - cashReserved;
    }

    /**
     * Lock money for a new BUY order, so it can't be spent twice.
     *
     * Example: balance 1,00,000, reserved 0, reserveCash(10,000)
     *          -> reserved becomes 10,000, available becomes 90,000
     *
     * Rules:
     *   1. amount must be greater than 0          -> otherwise throw IllegalArgumentException
     *   2. amount must be <= availableCash()      -> otherwise throw InsufficientBalanceException
     *   3. if both are OK                         -> add amount to cashReserved
     */
    public void reserveCash(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive, got " + amount);
        }
        if (amount > availableCash()) {
            throw new InsufficientBalanceException(
                    "Not enough cash: need " + amount + ", available " + availableCash());
        }
        cashReserved += amount;
    }

    /**
     * Unlock money when a BUY order is cancelled (or after it is filled, in Step 4).
     *
     * Example: reserved 10,000, releaseCash(10,000) -> reserved becomes 0
     *
     * Rules:
     *   1. amount must be greater than 0          -> otherwise throw IllegalArgumentException
     *   2. amount must be <= cashReserved         -> otherwise throw IllegalArgumentException
     *                                                (you can't release more than is locked)
     *   3. if both are OK                         -> subtract amount from cashReserved
     */
    public void releaseCash(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive, got " + amount);
        }
        if (amount > cashReserved) {
            throw new IllegalArgumentException(
                    "Cannot release " + amount + ": only " + cashReserved + " is reserved");
        }
        cashReserved -= amount;
    }

    // ============================== Step 4: settlement ==============================

    /**
     * BUYER side of a trade: pay for the shares and remove the lock that was holding the money.
     *
     *   reservedAmount = what was LOCKED for this part of the order  (my limit price x qty)
     *   cost           = what the trade ACTUALLY costs              (trade price   x qty)
     *
     * Example: Shivam bid Rs 100 for 5 shares, trade happened at Rs 98.
     *   settleBuy(reservedAmount = 500, cost = 490)
     *   -> cashReserved goes down by 500  (whole lock removed: 490 spent + 10 unlocked)
     *   -> cashBalance  goes down by 490  (only the real cost is paid)
     *
     * Rules:
     *   1. cost must be > 0                         -> otherwise IllegalArgumentException
     *   2. cost must be <= reservedAmount           -> otherwise IllegalArgumentException
     *                                                  (a buyer never pays more than they locked)
     *   3. reservedAmount must be <= cashReserved   -> otherwise IllegalArgumentException
     *   4. cashReserved -= reservedAmount
     *   5. cashBalance  -= cost
     */
    public void settleBuy(long reservedAmount, long cost) throws IllegalArgumentException {
      if(cost > reservedAmount) {
          throw new IllegalArgumentException("a buyer never pays more than they locked");

      }
      if(cost<=0){
          throw new IllegalArgumentException("cost will be positive ");
      }
      if (reservedAmount > cashReserved) {
            throw new IllegalArgumentException("cannot remove more lock than is reserved");
        }

      else{
          cashReserved=cashReserved-reservedAmount;
          cashBalance=cashBalance-cost;


        }

    }

    /**
     * SELLER side of a trade: receive the money.
     *
     * Example: Rahul sold 5 shares at Rs 98 -> receiveCash(490) -> cashBalance goes up by 490
     *
     * Rules:
     *   1. amount must be > 0   -> otherwise IllegalArgumentException
     *   2. cashBalance += amount
     */
    public void receiveCash(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive, got " + amount);

        }
        else
            {
            cashBalance=cashBalance+amount;
            }
    }

    public String getOwnerId() {
        return ownerId;
    }

    public long getCashBalance() {
        return cashBalance;
    }

    public long getCashReserved() {
        return cashReserved;
    }

    public Long getVersion() {
        return version;
    }
}
