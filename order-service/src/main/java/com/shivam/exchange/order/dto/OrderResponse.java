package com.shivam.exchange.order.dto;

import com.shivam.exchange.engine.Order;
import com.shivam.exchange.engine.OrderStatus;
import com.shivam.exchange.engine.OrderType;
import com.shivam.exchange.engine.Side;
import com.shivam.exchange.engine.Trade;

import java.util.List;

/** What the API returns after placing an order: the order's state plus any trades it caused. */
public record OrderResponse(
        long orderId,
        String symbol,
        Side side,
        OrderType type,
        long price,
        long quantity,
        long remainingQty,
        OrderStatus status,
        List<Trade> trades) {

    public static OrderResponse from(Order order, String symbol, List<Trade> trades) {
        return new OrderResponse(
                order.id(),
                symbol,
                order.side(),
                order.type(),
                order.price(),
                order.quantity(),
                order.remainingQty(),
                order.status(),
                trades);
    }
}
