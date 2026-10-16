package com.shivam.exchange.order.controller;

import com.shivam.exchange.order.dto.BookTopResponse;
import com.shivam.exchange.order.dto.OrderResponse;
import com.shivam.exchange.order.dto.PlaceOrderRequest;
import com.shivam.exchange.order.service.ExchangeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * The HTTP endpoints. A controller should stay thin: read the request, call the service,
 * return the result. All business logic lives in ExchangeService and the engine.
 */
@RestController
@RequestMapping("/api")
public class OrderController {

    private final ExchangeService exchange;

    // Spring sees this constructor and passes in the ExchangeService automatically ("dependency injection").
    public OrderController(ExchangeService exchange) {
        this.exchange = exchange;
    }

    /** Place an order. Returns 201 Created with the order and any trades. */
    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse placeOrder(@Valid @RequestBody PlaceOrderRequest request) {
        return exchange.placeOrder(request);
    }

    /** Cancel an open order. Returns 404 if it is unknown, filled or already cancelled. */
    @DeleteMapping("/orders/{orderId}")
    public Map<String, Object> cancel(@PathVariable long orderId) {
        exchange.cancel(orderId);
        return Map.of("orderId", orderId, "status", "CANCELLED");
    }

    /** Best bid and best ask for one stock. */
    @GetMapping("/market/{symbol}/top")
    public BookTopResponse topOfBook(@PathVariable String symbol) {
        return exchange.topOfBook(symbol);
    }

    /** The stocks you can trade. */
    @GetMapping("/market/symbols")
    public List<String> symbols() {
        return ExchangeService.SYMBOLS;
    }
}
