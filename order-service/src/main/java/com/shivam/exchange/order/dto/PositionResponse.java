package com.shivam.exchange.order.dto;

import com.shivam.exchange.order.entity.Position;

/** One stock holding, as returned by the API. */
public record PositionResponse(
        String symbol,
        long quantity,
        long quantityReserved,
        long availableQuantity) {

    public static PositionResponse from(Position position) {
        return new PositionResponse(
                position.getSymbol(),
                position.getQuantity(),
                position.getQuantityReserved(),
                position.availableQuantity());
    }
}
