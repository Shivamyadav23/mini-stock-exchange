package com.shivam.exchange.order.api.dto;

import com.shivam.exchange.engine.OrderType;
import com.shivam.exchange.engine.Side;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * The JSON body of POST /api/orders. Example:
 *
 *   {"ownerId":"shivam","symbol":"INFY","side":"BUY","type":"LIMIT","price":10000,"quantity":10}
 *
 * price is in PAISE (10000 = Rs 100.00) and is required only for LIMIT orders.
 * ownerId is sent by the client for now; on Day 5 it will come from the JWT login instead.
 */
public record PlaceOrderRequest(
        @NotBlank String ownerId,
        @NotBlank String symbol,
        @NotNull Side side,
        @NotNull OrderType type,
        @Positive Long price,
        @NotNull @Positive Long quantity) {
}
