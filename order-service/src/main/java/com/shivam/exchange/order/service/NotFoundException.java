package com.shivam.exchange.order.service;

/** Thrown for an unknown symbol or an order that is not open. The API turns it into HTTP 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
