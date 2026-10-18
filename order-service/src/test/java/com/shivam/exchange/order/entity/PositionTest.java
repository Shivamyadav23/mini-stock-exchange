package com.shivam.exchange.order.entity;

import com.shivam.exchange.order.exception.InsufficientBalanceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Plain unit tests for Position.reserveShares / releaseShares. */
class PositionTest {

    private Position position;

    @BeforeEach
    void setUp() {
        position = new Position("shivam", "INFY", 100);
    }

    @Test
    @DisplayName("Reserving shares locks them: available goes down, quantity stays the same")
    void reserveLocksShares() {
        position.reserveShares(30);

        assertThat(position.getQuantityReserved()).isEqualTo(30);
        assertThat(position.availableQuantity()).isEqualTo(70);
        assertThat(position.getQuantity()).isEqualTo(100);
    }

    @Test
    @DisplayName("Selling more shares than available is rejected and nothing changes")
    void reserveMoreThanAvailableFails() {
        position.reserveShares(80);

        assertThatThrownBy(() -> position.reserveShares(30))
                .isInstanceOf(InsufficientBalanceException.class);
        assertThat(position.getQuantityReserved()).isEqualTo(80);
    }

    @Test
    @DisplayName("Reserving zero or negative shares is rejected")
    void reserveZeroOrNegativeFails() {
        assertThatThrownBy(() -> position.reserveShares(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> position.reserveShares(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Releasing shares unlocks them again")
    void releaseUnlocksShares() {
        position.reserveShares(30);
        position.releaseShares(30);

        assertThat(position.getQuantityReserved()).isZero();
        assertThat(position.availableQuantity()).isEqualTo(100);
    }

    @Test
    @DisplayName("You can't release more shares than are reserved")
    void releaseMoreThanReservedFails() {
        position.reserveShares(10);

        assertThatThrownBy(() -> position.releaseShares(20))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(position.getQuantityReserved()).isEqualTo(10);
    }
}
