package com.shivam.exchange.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.shivam.exchange.engine.Side.BUY;
import static com.shivam.exchange.engine.Side.SELL;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every rule of the matching engine has a test here.
 * Run with: mvn test
 *
 * Prices are in paise: 10000 = Rs 100.00, 9800 = Rs 98.00
 */
class OrderBookTest {

    private OrderBook book;
    private long nextId;

    @BeforeEach
    void setUp() {
        book = new OrderBook("INFY");
        nextId = 1;
    }

    private Order limit(String owner, Side side, long price, long qty) {
        return Order.limit(nextId++, owner, side, price, qty);
    }

    private Order market(String owner, Side side, long qty) {
        return Order.market(nextId++, owner, side, qty);
    }

    @Nested
    @DisplayName("Level 1: orders that do not match just wait")
    class Resting {

        @Test
        @DisplayName("A buy into an empty book waits as the best bid")
        void buyIntoEmptyBookRests() {
            Order buy = limit("shivam", BUY, 10000, 10);

            List<Trade> trades = book.process(buy);

            assertThat(trades).isEmpty();
            assertThat(book.bestBid()).isEqualTo(10000);
            assertThat(buy.status()).isEqualTo(OrderStatus.NEW);
        }

        @Test
        @DisplayName("Buy at 100 does NOT match a seller at 102")
        void noMatchWhenPricesDoNotCross() {
            book.process(limit("rahul", SELL, 10200, 10));
            Order buy = limit("shivam", BUY, 10000, 10);

            List<Trade> trades = book.process(buy);

            assertThat(trades).isEmpty();
            assertThat(book.bestBid()).isEqualTo(10000);
            assertThat(book.bestAsk()).isEqualTo(10200);
        }
    }

    @Nested
    @DisplayName("Level 2: basic matching")
    class BasicMatch {

        @Test
        @DisplayName("Buy at 100 matches a seller at exactly 100")
        void exactPriceMatch() {
            Order sell = limit("rahul", SELL, 10000, 10);
            book.process(sell);
            Order buy = limit("shivam", BUY, 10000, 10);

            List<Trade> trades = book.process(buy);

            assertThat(trades).containsExactly(
                    new Trade(buy.id(), sell.id(), sell.id(), buy.id(), 10000, 10));
            assertThat(buy.status()).isEqualTo(OrderStatus.FILLED);
            assertThat(sell.status()).isEqualTo(OrderStatus.FILLED);
            assertThat(book.bestBid()).isNull();
            assertThat(book.bestAsk()).isNull();
        }

        @Test
        @DisplayName("Buy at 100 vs seller at 98: deal happens at 98 (the waiting order's price)")
        void tradeHappensAtRestingPrice() {
            Order sell = limit("rahul", SELL, 9800, 10);
            book.process(sell);

            List<Trade> trades = book.process(limit("shivam", BUY, 10000, 10));

            assertThat(trades).hasSize(1);
            assertThat(trades.get(0).price()).isEqualTo(9800);
        }

        @Test
        @DisplayName("A sell also matches: sell at 98 vs buyer waiting at 100 trades at 100")
        void sellSideMatches() {
            Order buy = limit("shivam", BUY, 10000, 10);
            book.process(buy);
            Order sell = limit("rahul", SELL, 9800, 10);

            List<Trade> trades = book.process(sell);

            assertThat(trades).containsExactly(
                    new Trade(buy.id(), sell.id(), buy.id(), sell.id(), 10000, 10));
        }
    }

    @Nested
    @DisplayName("Level 3: partial fills")
    class PartialFills {

        @Test
        @DisplayName("Buy 10 vs seller with only 4: get 4, remaining 6 waits in the book")
        void incomingPartiallyFilledRestsRemainder() {
            book.process(limit("rahul", SELL, 9900, 4));
            Order buy = limit("shivam", BUY, 10000, 10);

            List<Trade> trades = book.process(buy);

            assertThat(trades).hasSize(1);
            assertThat(trades.get(0).quantity()).isEqualTo(4);
            assertThat(buy.remainingQty()).isEqualTo(6);
            assertThat(buy.status()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
            assertThat(book.bestBid()).isEqualTo(10000);
            assertThat(book.quantityAt(BUY, 10000)).isEqualTo(6);
            assertThat(book.bestAsk()).isNull();
        }

        @Test
        @DisplayName("Buy 3 vs seller with 10: seller keeps 7 waiting")
        void restingPartiallyFilledStaysInBook() {
            Order sell = limit("rahul", SELL, 10000, 10);
            book.process(sell);

            book.process(limit("shivam", BUY, 10000, 3));

            assertThat(sell.remainingQty()).isEqualTo(7);
            assertThat(sell.status()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
            assertThat(book.quantityAt(SELL, 10000)).isEqualTo(7);
        }

        @Test
        @DisplayName("One buy sweeps several sellers, cheapest first")
        void sweepsMultipleLevels() {
            book.process(limit("a", SELL, 10100, 5));
            book.process(limit("b", SELL, 9900, 5));
            book.process(limit("c", SELL, 10500, 5));

            List<Trade> trades = book.process(limit("shivam", BUY, 10200, 12));

            assertThat(trades).extracting(Trade::price).containsExactly(9900L, 10100L);
            assertThat(trades).extracting(Trade::quantity).containsExactly(5L, 5L);
            assertThat(book.bestBid()).isEqualTo(10200);
            assertThat(book.quantityAt(BUY, 10200)).isEqualTo(2);
            assertThat(book.bestAsk()).isEqualTo(10500);
        }
    }

    @Nested
    @DisplayName("Level 4: time priority (same price -> first come, first served)")
    class TimePriority {

        @Test
        @DisplayName("Two sellers at 98: the one who came first is matched first")
        void firstInFirstOut() {
            Order first = limit("rahul", SELL, 9800, 5);
            Order second = limit("priya", SELL, 9800, 5);
            book.process(first);
            book.process(second);

            List<Trade> trades = book.process(limit("shivam", BUY, 10000, 5));

            assertThat(trades).hasSize(1);
            assertThat(trades.get(0).sellOrderId()).isEqualTo(first.id());
            assertThat(second.remainingQty()).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("Level 5: market orders")
    class MarketOrders {

        @Test
        @DisplayName("Market buy takes the best sellers at any price")
        void marketBuyMatchesAnyPrice() {
            book.process(limit("rahul", SELL, 50000, 5));

            List<Trade> trades = book.process(market("shivam", BUY, 5));

            assertThat(trades).hasSize(1);
            assertThat(trades.get(0).price()).isEqualTo(50000);
        }

        @Test
        @DisplayName("Market order never waits: unfilled part is cancelled")
        void marketRemainderIsCancelled() {
            book.process(limit("rahul", SELL, 10000, 3));
            Order buy = market("shivam", BUY, 10);

            book.process(buy);

            assertThat(buy.remainingQty()).isEqualTo(7);
            assertThat(buy.status()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(book.bestBid()).isNull();
        }
    }

    @Nested
    @DisplayName("Level 6: self-trade prevention and cancel")
    class SelfTradeAndCancel {

        @Test
        @DisplayName("You cannot trade with yourself: incoming order is cancelled")
        void selfTradeIsPrevented() {
            Order mySell = limit("shivam", SELL, 10000, 5);
            book.process(mySell);
            Order myBuy = limit("shivam", BUY, 10000, 5);

            List<Trade> trades = book.process(myBuy);

            assertThat(trades).isEmpty();
            assertThat(myBuy.status()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(book.isResting(mySell.id())).isTrue();
        }

        @Test
        @DisplayName("A cancelled order is removed from the book")
        void cancelRemovesOrder() {
            Order buy = limit("shivam", BUY, 10000, 5);
            book.process(buy);

            boolean cancelled = book.cancel(buy.id());

            assertThat(cancelled).isTrue();
            assertThat(buy.status()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(book.bestBid()).isNull();
            assertThat(book.cancel(buy.id())).isFalse();
        }
    }
}
