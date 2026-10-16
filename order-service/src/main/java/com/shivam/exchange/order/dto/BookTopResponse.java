package com.shivam.exchange.order.dto;

/**
 * Best buyer and best seller for one stock ("top of book" / "L1").
 * bestBid or bestAsk is null when that side of the book is empty.
 */
public record BookTopResponse(
        String symbol,
        Long bestBid,
        long bidQty,
        Long bestAsk,
        long askQty) {
}
