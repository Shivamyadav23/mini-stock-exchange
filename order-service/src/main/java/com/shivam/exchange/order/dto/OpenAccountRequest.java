package com.shivam.exchange.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * JSON body of POST /api/accounts. Example: {"ownerId":"shivam"}
 *
 * ownerId rules: 3 to 30 characters, only letters, digits and underscore.
 * Anything else (spaces, symbols, too short) is rejected with HTTP 400 before reaching the service.
 */
public record OpenAccountRequest(
        @NotBlank
        @Pattern(regexp = "[A-Za-z0-9_]{3,30}",
                message = "must be 3-30 characters: letters, digits or underscore")
        String ownerId) {
}
