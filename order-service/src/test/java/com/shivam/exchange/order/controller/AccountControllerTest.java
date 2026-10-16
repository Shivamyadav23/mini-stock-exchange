package com.shivam.exchange.order.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for the account endpoints. Uses the in-memory test database
 * (see src/test/resources/application.properties).
 *
 * All test classes share one running app and one database, so every test
 * uses its own unique ownerId to stay independent.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AccountControllerTest {

    @Autowired
    private MockMvc mvc;

    private ResultActions openAccount(String ownerId) throws Exception {
        return mvc.perform(post("/api/accounts").contentType(APPLICATION_JSON)
                .content("{\"ownerId\":\"" + ownerId + "\"}"));
    }

    @Test
    @DisplayName("New account gets Rs 1,00,000 and 100 shares of each of the 5 stocks")
    void openAccountGivesStartingCashAndShares() throws Exception {
        openAccount("neha")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ownerId").value("neha"))
                .andExpect(jsonPath("$.cashBalance").value(10_000_000))
                .andExpect(jsonPath("$.cashReserved").value(0))
                .andExpect(jsonPath("$.availableCash").value(10_000_000))
                .andExpect(jsonPath("$.positions.length()").value(5))
                .andExpect(jsonPath("$.positions[0].symbol").value("HDFCBANK"))   // sorted A-Z
                .andExpect(jsonPath("$.positions[0].quantity").value(100))
                .andExpect(jsonPath("$.positions[0].availableQuantity").value(100));
    }

    @Test
    @DisplayName("Opening the same account twice returns 409 Conflict")
    void duplicateAccountIsConflict() throws Exception {
        openAccount("dup_user").andExpect(status().isCreated());
        openAccount("dup_user").andExpect(status().isConflict());
    }

    @Test
    @DisplayName("GET returns an account that was opened, saved in the database")
    void getAccountAfterOpening() throws Exception {
        openAccount("getter_user").andExpect(status().isCreated());

        mvc.perform(get("/api/accounts/getter_user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerId").value("getter_user"))
                .andExpect(jsonPath("$.cashBalance").value(10_000_000))
                .andExpect(jsonPath("$.positions.length()").value(5));
    }

    @Test
    @DisplayName("GET for an unknown account returns 404")
    void unknownAccountIsNotFound() throws Exception {
        mvc.perform(get("/api/accounts/nobody_here"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Invalid ownerId (space, too short) is rejected with 400")
    void invalidOwnerIdIsBadRequest() throws Exception {
        openAccount("a b").andExpect(status().isBadRequest());
        openAccount("ab").andExpect(status().isBadRequest());
    }
}
