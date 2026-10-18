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
}
