package com.shivam.exchange.order.dto;

import com.shivam.exchange.order.entity.Account;
import com.shivam.exchange.order.entity.Position;

import java.util.List;

/**
 * What the API returns for an account: cash (in paise) plus all stock holdings.
 *
 * Why a DTO and not the Account entity itself?
 *  - The API shape stays stable even if the database table changes.
 *  - We choose exactly what the client sees (e.g. no internal "version" field).
 *  - We can add computed values like availableCash.
 */
public record AccountResponse(
        String ownerId,
        long cashBalance,
        long cashReserved,
        long availableCash,
        List<PositionResponse> positions) {

    public static AccountResponse from(Account account, List<Position> positions) {
        return new AccountResponse(
                account.getOwnerId(),
                account.getCashBalance(),
                account.getCashReserved(),
                account.availableCash(),
                positions.stream().map(PositionResponse::from).toList());
    }
}
