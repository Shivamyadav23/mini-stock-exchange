package com.shivam.exchange.order.controller;

import com.shivam.exchange.order.dto.AccountResponse;
import com.shivam.exchange.order.dto.OpenAccountRequest;
import com.shivam.exchange.order.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP endpoints for accounts. Thin on purpose: validate input, call the service, return the result.
 *
 *   POST /api/accounts            {"ownerId":"shivam"}  -> 201 Created (or 409 if it exists)
 *   GET  /api/accounts/{ownerId}                        -> 200 OK      (or 404 if unknown)
 */
@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse openAccount(@Valid @RequestBody OpenAccountRequest request) {
        return accountService.openAccount(request.ownerId());
    }

    @GetMapping("/{ownerId}")
    public AccountResponse getAccount(@PathVariable String ownerId) {
        return accountService.getAccount(ownerId);
    }
}
