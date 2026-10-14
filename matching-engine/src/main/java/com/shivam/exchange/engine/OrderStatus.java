package com.shivam.exchange.engine;

/**
 * NEW -> PARTIALLY_FILLED -> FILLED
 *  |           |
 *  +-----------+--> CANCELLED
 */
public enum OrderStatus {
    NEW,
    PARTIALLY_FILLED,
    FILLED,
    CANCELLED
}
