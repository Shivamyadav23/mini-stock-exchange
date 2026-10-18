package com.shivam.exchange.order.exception;

/**
 * Thrown when a user tries to reserve more cash or shares than they have available.
 * Example: Rs 90,000 available but the BUY order needs Rs 1,00,000.
 * The API turns it into HTTP 400 Bad Request.
 */
public class InsufficientBalanceException extends RuntimeException {

    public InsufficientBalanceException(String message) {
        super(message);
    }
}
