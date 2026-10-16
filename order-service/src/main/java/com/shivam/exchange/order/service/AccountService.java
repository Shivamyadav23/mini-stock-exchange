package com.shivam.exchange.order.service;

import com.shivam.exchange.order.dto.AccountResponse;
import com.shivam.exchange.order.entity.Account;
import com.shivam.exchange.order.entity.Position;
import com.shivam.exchange.order.exception.AlreadyExistsException;
import com.shivam.exchange.order.exception.NotFoundException;
import com.shivam.exchange.order.repository.AccountRepository;
import com.shivam.exchange.order.repository.PositionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Business logic for paper-trading accounts.
 *
 * Every new account starts with:
 *   - Rs 1,00,000 fake cash (10_000_000 paise)
 *   - 100 shares of each tradable stock (so users can SELL from day one)
 */
@Service
public class AccountService {

    public static final long STARTING_CASH = 10_000_000L;   // Rs 1,00,000 in paise
    public static final long STARTING_SHARES = 100L;

    private final AccountRepository accountRepository;
    private final PositionRepository positionRepository;

    // Spring passes both repositories in automatically (constructor injection).
    public AccountService(AccountRepository accountRepository, PositionRepository positionRepository) {
        this.accountRepository = accountRepository;
        this.positionRepository = positionRepository;
    }

    /**
     * Open a new account.
     *
     * @Transactional = ALL OR NOTHING. Every save() below runs in ONE database transaction.
     * If anything fails halfway (e.g. on the 3rd stock), the database rolls back everything,
     * so we never end up with an account that has only 2 of its 5 stocks.
     */
    @Transactional
    public AccountResponse openAccount(String ownerId) {
        // 1. One account per user.
        if (accountRepository.existsById(ownerId)) {
            throw new AlreadyExistsException("Account '" + ownerId + "' already exists");
        }

        // 2-3. Create and save the cash account.
        Account account = accountRepository.save(new Account(ownerId, STARTING_CASH));

        // 4. Give the user 100 shares of every stock.
        List<Position> positions = new ArrayList<>();
        for (String symbol : ExchangeService.SYMBOLS) {
            positions.add(positionRepository.save(new Position(ownerId, symbol, STARTING_SHARES)));
        }
        positions.sort(Comparator.comparing(Position::getSymbol));   // same order as getAccount()

        // 5. Return what the API should show.
        return AccountResponse.from(account, positions);
    }

    /**
     * Get an account with all its positions.
     * readOnly = true: a small optimisation that tells Hibernate nothing will be changed.
     */
    /**
     * Throws NotFoundException (HTTP 404) if the user has no account.
     * Used before placing an order: no account, no trading.
     */
    @Transactional(readOnly = true)
    public void requireAccount(String ownerId) {
        if (!accountRepository.existsById(ownerId)) {
            throw new NotFoundException("No account for '" + ownerId + "'. Open one with POST /api/accounts");
        }
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccount(String ownerId) {
        Account account = accountRepository.findById(ownerId)
                .orElseThrow(() -> new NotFoundException("No account for '" + ownerId + "'"));
        return AccountResponse.from(account, positionRepository.findByOwnerIdOrderBySymbolAsc(ownerId));
    }
}
