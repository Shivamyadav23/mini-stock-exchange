package com.shivam.exchange.order.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Starts the whole Spring app (without a real network port) and sends fake HTTP requests to it.
 *
 * NOTE: all tests share ONE running app, so the order books keep their state between tests.
 * That's why each test uses a different stock symbol: tests must not affect each other.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerTest {

    @Autowired
    private MockMvc mvc;

    private MvcResult placeOrder(String json) throws Exception {
        return mvc.perform(post("/api/orders").contentType(APPLICATION_JSON).content(json))
                .andReturn();
    }

    @Test
    @DisplayName("Sell at 98, then buy at 100: a trade happens at 98")
    void sellThenBuyCreatesTrade() throws Exception {
        mvc.perform(post("/api/orders").contentType(APPLICATION_JSON).content("""
                        {"ownerId":"rahul","symbol":"INFY","side":"SELL","type":"LIMIT","price":9800,"quantity":5}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.trades").isEmpty());

        mvc.perform(post("/api/orders").contentType(APPLICATION_JSON).content("""
                        {"ownerId":"shivam","symbol":"INFY","side":"BUY","type":"LIMIT","price":10000,"quantity":5}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FILLED"))
                .andExpect(jsonPath("$.trades.length()").value(1))
                .andExpect(jsonPath("$.trades[0].price").value(9800))
                .andExpect(jsonPath("$.trades[0].quantity").value(5));
    }

    @Test
    @DisplayName("Top of book shows the best waiting buyer")
    void topOfBook() throws Exception {
        placeOrder("""
                {"ownerId":"amit","symbol":"RELIANCE","side":"BUY","type":"LIMIT","price":250000,"quantity":3}
                """);

        mvc.perform(get("/api/market/RELIANCE/top"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bestBid").value(250000))
                .andExpect(jsonPath("$.bidQty").value(3))
                .andExpect(jsonPath("$.askQty").value(0));
    }

    @Test
    @DisplayName("Cancel an open order works once; second cancel is 404")
    void cancelOrder() throws Exception {
        MvcResult placed = placeOrder("""
                {"ownerId":"priya","symbol":"SBIN","side":"SELL","type":"LIMIT","price":80000,"quantity":2}
                """);
        Number orderId = JsonPath.read(placed.getResponse().getContentAsString(), "$.orderId");

        mvc.perform(delete("/api/orders/" + orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mvc.perform(delete("/api/orders/" + orderId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("LIMIT order without a price is rejected with 400")
    void limitWithoutPriceIsBadRequest() throws Exception {
        mvc.perform(post("/api/orders").contentType(APPLICATION_JSON).content("""
                        {"ownerId":"shivam","symbol":"TCS","side":"BUY","type":"LIMIT","quantity":5}
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Quantity 0 fails validation with 400")
    void zeroQuantityIsBadRequest() throws Exception {
        mvc.perform(post("/api/orders").contentType(APPLICATION_JSON).content("""
                        {"ownerId":"shivam","symbol":"TCS","side":"BUY","type":"LIMIT","price":100,"quantity":0}
                        """))
                .andExpect(status().isBadRequest());

    }

    @Test
    @DisplayName("Unknown symbol returns 404")
    void unknownSymbolIsNotFound() throws Exception {
        mvc.perform(get("/api/market/APPLE/top"))
                .andExpect(status().isNotFound());
    }
}
