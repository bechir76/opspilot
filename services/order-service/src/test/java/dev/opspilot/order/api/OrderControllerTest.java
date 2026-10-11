package dev.opspilot.order.api;

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

import java.util.List;

import dev.opspilot.order.order.Order;
import dev.opspilot.order.order.OrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private OrderService orders;

    @Test
    void confirmedOrderIs201() throws Exception {
        Order order = new Order("KB-001", 2);
        order.confirm();
        when(orders.placeOrder("KB-001", 2)).thenReturn(order);

        mvc.perform(placeOrder("{\"sku\":\"KB-001\",\"quantity\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.sku").value("KB-001"));
    }

    @Test
    void rejectedOrderIs409() throws Exception {
        Order order = new Order("WC-001", 50);
        order.reject("Insufficient stock for WC-001: requested 50, available 3");
        when(orders.placeOrder("WC-001", 50)).thenReturn(order);

        mvc.perform(placeOrder("{\"sku\":\"WC-001\",\"quantity\":50}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.orderStatus").value("REJECTED"))
                .andExpect(jsonPath("$.detail").value("Insufficient stock for WC-001: requested 50, available 3"));
    }

    @Test
    void failedOrderIs502() throws Exception {
        Order order = new Order("KB-001", 1);
        order.fail("I/O error: Read timed out");
        when(orders.placeOrder("KB-001", 1)).thenReturn(order);

        mvc.perform(placeOrder("{\"sku\":\"KB-001\",\"quantity\":1}"))
                .andExpect(status().isBadGateway())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Inventory unavailable"))
                .andExpect(jsonPath("$.orderStatus").value("FAILED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"sku\":\"KB-001\",\"quantity\":0}", "{\"sku\":\"KB-001\",\"quantity\":101}",
            "{\"sku\":\"\",\"quantity\":1}", "{\"quantity\":1}", "not json"})
    void invalidOrderIs400(String body) throws Exception {
        mvc.perform(placeOrder(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

        verify(orders, never()).placeOrder(anyString(), anyInt());
    }

    @Test
    void listsLatestOrdersWithDefaultLimit() throws Exception {
        when(orders.latestOrders(20)).thenReturn(List.of(new Order("KB-001", 1)));

        mvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sku").value("KB-001"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "101"})
    void limitOutOfRangeIs400(String limit) throws Exception {
        mvc.perform(get("/api/orders").param("limit", limit))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    private static RequestBuilder placeOrder(String body) {
        return post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
