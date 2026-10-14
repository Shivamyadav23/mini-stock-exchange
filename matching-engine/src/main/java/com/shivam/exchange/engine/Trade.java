package com.shivam.exchange.engine;

/**
 * One deal between a buyer and a seller.
 *
 * makerOrderId = the order that was already WAITING in the book (it "made" liquidity).
 * takerOrderId = the NEW order that came in and matched it (it "took" liquidity).
 * price is always the maker's price.
 */
public record Trade(
        long buyOrderId,
        long sellOrderId,
        long makerOrderId,
        long takerOrderId,
        long price,
        long quantity) {
}
