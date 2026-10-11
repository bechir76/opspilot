package dev.opspilot.inventory.api;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import dev.opspilot.inventory.product.InsufficientStockException;
import dev.opspilot.inventory.product.InventoryService;
import dev.opspilot.inventory.product.Product;
import dev.opspilot.inventory.product.ProductNotFoundException;
import dev.opspilot.inventory.product.Reservation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private InventoryService inventory;

    @Test
    void listsProducts() throws Exception {
        when(inventory.listProducts()).thenReturn(List.of(
                new Product("KB-001", "Mechanical Keyboard", new BigDecimal("89.90"), 5000)));

        mvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sku").value("KB-001"))
                .andExpect(jsonPath("$[0].price").value(89.90))
                .andExpect(jsonPath("$[0].id").doesNotExist());
    }

    @Test
    void getsProductBySku() throws Exception {
        when(inventory.getProduct("KB-001"))
                .thenReturn(new Product("KB-001", "Mechanical Keyboard", new BigDecimal("89.90"), 5000));

        mvc.perform(get("/api/products/KB-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(5000));
    }

    @Test
    void unknownProductIs404() throws Exception {
        when(inventory.getProduct("NOPE")).thenThrow(new ProductNotFoundException("NOPE"));

        mvc.perform(get("/api/products/NOPE"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Product not found"));
    }

    @Test
    void reservationReturns200() throws Exception {
        when(inventory.reserve("KB-001", 2)).thenReturn(new Reservation("KB-001", 2, 4998));

        mvc.perform(reservation("KB-001", "{\"quantity\": 2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("KB-001"))
                .andExpect(jsonPath("$.reserved").value(2))
                .andExpect(jsonPath("$.remaining").value(4998));
    }

    @Test
    void reservationOfUnknownProductIs404() throws Exception {
        when(inventory.reserve("NOPE", 1)).thenThrow(new ProductNotFoundException("NOPE"));

        mvc.perform(reservation("NOPE", "{\"quantity\": 1}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void insufficientStockIs409() throws Exception {
        when(inventory.reserve("WC-001", 50)).thenThrow(new InsufficientStockException("WC-001", 50, 3));

        mvc.perform(reservation("WC-001", "{\"quantity\": 50}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Insufficient stock for WC-001: requested 50, available 3"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"quantity\": 0}", "{\"quantity\": 101}", "{\"quantity\": -1}", "{}", "not json"})
    void invalidQuantityIs400(String body) throws Exception {
        mvc.perform(reservation("KB-001", body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

        verify(inventory, never()).reserve(anyString(), anyInt());
    }

    private static org.springframework.test.web.servlet.RequestBuilder reservation(String sku, String body) {
        return post("/api/products/{sku}/reservations", sku).contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
