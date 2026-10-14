package com.shivam.exchange.engine;

/**
 * LIMIT  = "buy/sell at this price or better". Unfilled quantity waits in the book.
 * MARKET = "buy/sell right now at any price". Unfilled quantity is cancelled, never waits.
 */
public enum OrderType {
    LIMIT,
    MARKET
}
