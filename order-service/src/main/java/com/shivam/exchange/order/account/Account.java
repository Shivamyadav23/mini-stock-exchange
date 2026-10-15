package com.shivam.exchange.order.account;

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
