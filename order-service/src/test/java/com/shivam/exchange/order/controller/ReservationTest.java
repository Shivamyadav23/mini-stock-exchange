package com.shivam.exchange.order.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Step 3: placing an order locks cash (BUY) or shares (SELL); cancelling unlocks them.
 *
 * All tests use HDFCBANK with BUY prices far below SELL prices, so these orders never
 * match each other. Each test has its own user. Prices in paise.
 * New accounts: cash 10_000_000 (Rs 1,00,000) and 100 shares of each stock.
 * In GET /api/accounts, positions are sorted A-Z, so positions[0] is HDFCBANK.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReservationTest {

    @Autowired
    private MockMvc mvc;

    private void openAccount(String ownerId) throws Exception {
        mvc.perform(post("/api/accounts").contentType(APPLICATION_JSON)
                        .content("{\"ownerId\":\"" + ownerId + "\"}"))
                .andExpect(status().isCreated());
    }

    private ResultActions order(String ownerId, String side, String type, Long price, long qty) throws Exception {
        String priceJson = price == null ? "" : ",\"price\":" + price;
        return mvc.perform(post("/api/orders").contentType(APPLICATION_JSON).content(
                "{\"ownerId\":\"" + ownerId + "\",\"symbol\":\"HDFCBANK\",\"side\":\"" + side
                        + "\",\"type\":\"" + type + "\"" + priceJson + ",\"quantity\":" + qty + "}"));
    }

    private ResultActions account(String ownerId) throws Exception {
        return mvc.perform(get("/api/accounts/" + ownerId)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("BUY locks price x quantity cash")
    void buyReservesCash() throws Exception {
        openAccount("res_buy");

        order("res_buy", "BUY", "LIMIT", 10_000L, 5).andExpect(status().isCreated());   // 5 x Rs 100 = Rs 500

        account("res_buy")
                .andExpect(jsonPath("$.cashBalance").value(10_000_000))
                .andExpect(jsonPath("$.cashReserved").value(50_000))
                .andExpect(jsonPath("$.availableCash").value(9_950_000));
    }

    @Test
    @DisplayName("BUY costing more than available cash is rejected (400) and nothing is locked")
    void buyWithoutEnoughCashIsRejected() throws Exception {
        openAccount("res_poor");

        // 2 shares x Rs 1,00,000 = Rs 2,00,000 > Rs 1,00,000 available
        order("res_poor", "BUY", "LIMIT", 10_000_000L, 2).andExpect(status().isBadRequest());

        account("res_poor").andExpect(jsonPath("$.cashReserved").value(0));
    }

    @Test
    @DisplayName("SELL locks shares")
    void sellReservesShares() throws Exception {
        openAccount("res_sell");

        order("res_sell", "SELL", "LIMIT", 90_000_000L, 30).andExpect(status().isCreated());

        account("res_sell")
                .andExpect(jsonPath("$.positions[0].symbol").value("HDFCBANK"))
                .andExpect(jsonPath("$.positions[0].quantityReserved").value(30))
                .andExpect(jsonPath("$.positions[0].availableQuantity").value(70));
    }

    @Test
    @DisplayName("SELL more shares than owned is rejected (400): no short selling")
    void sellMoreThanOwnedIsRejected() throws Exception {
        openAccount("res_short");

        order("res_short", "SELL", "LIMIT", 90_000_000L, 101).andExpect(status().isBadRequest());

        account("res_short").andExpect(jsonPath("$.positions[0].quantityReserved").value(0));
    }

    @Test
    @DisplayName("The same shares can't be sold twice")
    void cannotSellSameSharesTwice() throws Exception {
        openAccount("res_twice");

        order("res_twice", "SELL", "LIMIT", 90_000_000L, 80).andExpect(status().isCreated());
        order("res_twice", "SELL", "LIMIT", 90_000_000L, 30).andExpect(status().isBadRequest());   // only 20 left
    }

    @Test
    @DisplayName("Cancelling a BUY unlocks the cash")
    void cancelBuyReleasesCash() throws Exception {
        openAccount("res_cancel_buy");
        String body = order("res_cancel_buy", "BUY", "LIMIT", 10_000L, 5)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Number orderId = JsonPath.read(body, "$.orderId");

        mvc.perform(delete("/api/orders/" + orderId)).andExpect(status().isOk());

        account("res_cancel_buy")
                .andExpect(jsonPath("$.cashReserved").value(0))
                .andExpect(jsonPath("$.availableCash").value(10_000_000));
    }

    @Test
    @DisplayName("Cancelling a SELL unlocks the shares")
    void cancelSellReleasesShares() throws Exception {
        openAccount("res_cancel_sell");
        String body = order("res_cancel_sell", "SELL", "LIMIT", 90_000_000L, 40)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Number orderId = JsonPath.read(body, "$.orderId");

        mvc.perform(delete("/api/orders/" + orderId)).andExpect(status().isOk());

        account("res_cancel_sell")
                .andExpect(jsonPath("$.positions[0].quantityReserved").value(0))
                .andExpect(jsonPath("$.positions[0].availableQuantity").value(100));
    }

    @Test
    @DisplayName("MARKET BUY is rejected for now (cost unknown up front)")
    void marketBuyIsRejected() throws Exception {
        openAccount("res_market");

        order("res_market", "BUY", "MARKET", null, 1).andExpect(status().isBadRequest());
    }
}
