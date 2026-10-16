package com.shivam.exchange.order.exception;

/** Thrown when something already exists (e.g. opening an account twice). The API turns it into HTTP 409. */
public class AlreadyExistsException extends RuntimeException {

    public AlreadyExistsException(String message) {
        super(message);
    }
}
