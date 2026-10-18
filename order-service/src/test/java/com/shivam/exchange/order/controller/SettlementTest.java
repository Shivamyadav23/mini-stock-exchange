package com.shivam.exchange.order.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Step 4: after a trade, money really moves buyer -> seller and shares seller -> buyer.
 *
 * Uses TCS. In GET /api/accounts positions are sorted A-Z:
 * HDFCBANK(0), INFY(1), RELIANCE(2), SBIN(3), TCS(4), so TCS is positions[4].
 * New account: cash 10_000_000 paise (Rs 1,00,000), 100 shares of each stock.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SettlementTest {

    @Autowired
    private MockMvc mvc;

    private void openAccount(String ownerId) throws Exception {
        mvc.perform(post("/api/accounts").contentType(APPLICATION_JSON)
                        .content("{\"ownerId\":\"" + ownerId + "\"}"))
                .andExpect(status().isCreated());
    }

    private String limit(String ownerId, String side, long price, long qty) throws Exception {
        return mvc.perform(post("/api/orders").contentType(APPLICATION_JSON).content(
                        "{\"ownerId\":\"" + ownerId + "\",\"symbol\":\"TCS\",\"side\":\"" + side
                                + "\",\"type\":\"LIMIT\",\"price\":" + price + ",\"quantity\":" + qty + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private ResultActions account(String ownerId) throws Exception {
        return mvc.perform(get("/api/accounts/" + ownerId)).andExpect(status().isOk());
    }

    private long cash(String ownerId) throws Exception {
        String body = account(ownerId).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.cashBalance")).longValue();
    }

    @Test
    @DisplayName("Rahul sells 5 TCS at Rs 98, Shivam bids Rs 100: Rs 490 and 5 shares change hands")
    void fullTradeMovesCashAndShares() throws Exception {
        openAccount("set_seller");
        openAccount("set_buyer");

        limit("set_seller", "SELL", 9_800, 5);
        limit("set_buyer", "BUY", 10_000, 5);       // trades at the seller's price: 5 x 9_800 = 49_000

        account("set_buyer")
                .andExpect(jsonPath("$.cashBalance").value(9_951_000))     // paid Rs 490
                .andExpect(jsonPath("$.cashReserved").value(0))            // Rs 500 lock fully removed
                .andExpect(jsonPath("$.positions[4].symbol").value("TCS"))
                .andExpect(jsonPath("$.positions[4].quantity").value(105));

        account("set_seller")
                .andExpect(jsonPath("$.cashBalance").value(10_049_000))    // received Rs 490
                .andExpect(jsonPath("$.positions[4].quantity").value(95))
                .andExpect(jsonPath("$.positions[4].quantityReserved").value(0));
    }

    @Test
    @DisplayName("Money conservation: total cash of buyer + seller is the same before and after")
    void totalCashIsConserved() throws Exception {
        openAccount("cons_seller");
        openAccount("cons_buyer");
        long before = cash("cons_seller") + cash("cons_buyer");

        limit("cons_seller", "SELL", 12_300, 7);
        limit("cons_buyer", "BUY", 15_000, 7);

        long after = cash("cons_seller") + cash("cons_buyer");
        assertThat(after).isEqualTo(before);
    }

    @Test
    @DisplayName("Partial fill: 4 of 10 shares trade; the other 6 stay locked until cancelled")
    void partialFillSettlesOnlyTradedShares() throws Exception {
        openAccount("part_seller");
        openAccount("part_buyer");

        String sell = limit("part_seller", "SELL", 20_000, 10);
        limit("part_buyer", "BUY", 20_000, 4);     // 4 x 20_000 = 80_000

        account("part_seller")
                .andExpect(jsonPath("$.cashBalance").value(10_080_000))
                .andExpect(jsonPath("$.positions[4].quantity").value(96))
                .andExpect(jsonPath("$.positions[4].quantityReserved").value(6));
        account("part_buyer")
                .andExpect(jsonPath("$.cashBalance").value(9_920_000))
                .andExpect(jsonPath("$.positions[4].quantity").value(104));

        // Cancel the remaining 6 -> unlocked (also keeps the TCS book clean for other tests)
        Number sellId = JsonPath.read(sell, "$.orderId");
        mvc.perform(delete("/api/orders/" + sellId)).andExpect(status().isOk());

        account("part_seller")
                .andExpect(jsonPath("$.positions[4].quantityReserved").value(0))
                .andExpect(jsonPath("$.positions[4].availableQuantity").value(96));
    }
}
