package com.shivam.exchange.order.entity;

import com.shivam.exchange.order.exception.InsufficientBalanceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Plain unit tests for Account.reserveCash / releaseCash. No Spring, no database: runs in milliseconds.
 * Amounts in paise: 10_000_000 = Rs 1,00,000 and 1_000_000 = Rs 10,000.
 */
class AccountTest {

    private Account account;

    @BeforeEach
    void setUp() {
        account = new Account("shivam", 10_000_000);
    }

    @Test
    @DisplayName("Reserving cash locks it: available goes down, balance stays the same")
    void reserveLocksCash() {
        account.reserveCash(1_000_000);

        assertThat(account.getCashReserved()).isEqualTo(1_000_000);
        assertThat(account.availableCash()).isEqualTo(9_000_000);
        assertThat(account.getCashBalance()).isEqualTo(10_000_000);
    }

    @Test
    @DisplayName("Two reservations add up")
    void reservationsAddUp() {
        account.reserveCash(1_000_000);
        account.reserveCash(2_000_000);

        assertThat(account.getCashReserved()).isEqualTo(3_000_000);
        assertThat(account.availableCash()).isEqualTo(7_000_000);
    }

    @Test
    @DisplayName("You can reserve exactly everything you have")
    void reserveAllAvailableCash() {
        account.reserveCash(10_000_000);

        assertThat(account.availableCash()).isZero();
    }

    @Test
    @DisplayName("Reserving more than available is rejected and nothing changes")
    void reserveMoreThanAvailableFails() {
        account.reserveCash(9_000_000);

        assertThatThrownBy(() -> account.reserveCash(2_000_000))
                .isInstanceOf(InsufficientBalanceException.class);
        assertThat(account.getCashReserved()).isEqualTo(9_000_000);
    }

    @Test
    @DisplayName("Reserving zero or a negative amount is rejected")
    void reserveZeroOrNegativeFails() {
        assertThatThrownBy(() -> account.reserveCash(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> account.reserveCash(-5)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Releasing cash unlocks it again")
    void releaseUnlocksCash() {
        account.reserveCash(1_000_000);
        account.releaseCash(1_000_000);

        assertThat(account.getCashReserved()).isZero();
        assertThat(account.availableCash()).isEqualTo(10_000_000);
    }

    @Test
    @DisplayName("You can't release more than is reserved")
    void releaseMoreThanReservedFails() {
        account.reserveCash(1_000_000);

        assertThatThrownBy(() -> account.releaseCash(2_000_000))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(account.getCashReserved()).isEqualTo(1_000_000);
    }

    @Test
    @DisplayName("Releasing zero or a negative amount is rejected")
    void releaseZeroOrNegativeFails() {
        account.reserveCash(1_000_000);

        assertThatThrownBy(() -> account.releaseCash(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> account.releaseCash(-5)).isInstanceOf(IllegalArgumentException.class);
    }

    // ============================== Step 4: settlement ==============================

    @Test
    @DisplayName("settleBuy: pays the real cost and removes the whole lock (price improvement unlocked)")
    void settleBuyPaysCostAndRemovesLock() {
        account.reserveCash(50_000);              // bid Rs 100 x 5 = Rs 500 locked

        account.settleBuy(50_000, 49_000);        // traded at Rs 98 x 5 = Rs 490

        assertThat(account.getCashReserved()).isZero();                 // whole lock removed
        assertThat(account.getCashBalance()).isEqualTo(9_951_000);      // only Rs 490 paid
        assertThat(account.availableCash()).isEqualTo(9_951_000);       // the Rs 10 is free again
    }

    @Test
    @DisplayName("settleBuy: a buyer can't pay more than they locked")
    void settleBuyCostAboveReservedFails() {
        account.reserveCash(50_000);

        assertThatThrownBy(() -> account.settleBuy(50_000, 60_000))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(account.getCashBalance()).isEqualTo(10_000_000);
        assertThat(account.getCashReserved()).isEqualTo(50_000);
    }

    @Test
    @DisplayName("settleBuy: can't remove more lock than exists")
    void settleBuyMoreThanReservedFails() {
        account.reserveCash(10_000);

        assertThatThrownBy(() -> account.settleBuy(50_000, 49_000))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(account.getCashBalance()).isEqualTo(10_000_000);
    }

    @Test
    @DisplayName("settleBuy: zero or negative cost is rejected")
    void settleBuyZeroCostFails() {
        account.reserveCash(10_000);

        assertThatThrownBy(() -> account.settleBuy(10_000, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("receiveCash: seller's balance goes up")
    void receiveCashAddsToBalance() {
        account.receiveCash(49_000);

        assertThat(account.getCashBalance()).isEqualTo(10_049_000);
        assertThat(account.getCashReserved()).isZero();
    }

    @Test
    @DisplayName("receiveCash: zero or negative is rejected")
    void receiveCashZeroOrNegativeFails() {
        assertThatThrownBy(() -> account.receiveCash(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> account.receiveCash(-1)).isInstanceOf(IllegalArgumentException.class);
    }
}
