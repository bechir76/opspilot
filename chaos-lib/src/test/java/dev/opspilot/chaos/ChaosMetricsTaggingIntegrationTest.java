package dev.opspilot.chaos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Boots a real Spring MVC + Micrometer stack with chaos-lib auto-configured, and proves that an injected
 * 500 is recorded in {@code http.server.requests} under the real route pattern, like an organic failure.
 */
@SpringBootTest(classes = ChaosMetricsTaggingIntegrationTest.TestApp.class)
@AutoConfigureMockMvc
class ChaosMetricsTaggingIntegrationTest {

    private static final String ROUTE = "/api/products/{sku}/reservations";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MeterRegistry registry;

    @Autowired
    private ChaosState state;

    @AfterEach
    void reset() {
        state.reset();
    }

    @Test
    void injected500IsTaggedWithTheRealRoutePattern() throws Exception {
        state.setErrorRate(1.0);

        mvc.perform(post("/api/products/KB-001/reservations")).andExpect(status().isInternalServerError());

        assertThat(registry.get("http.server.requests")
                .tag("uri", ROUTE)
                .tag("status", "500")
                .tag("outcome", "SERVER_ERROR")
                .timer()
                .count()).isEqualTo(1);
        assertThat(registry.find("http.server.requests").meters())
                .extracting(meter -> meter.getId().getTag("uri"))
                .doesNotContain("UNKNOWN");
    }

    @Test
    void healthyRequestIsTaggedTheSameWay() throws Exception {
        mvc.perform(post("/api/products/KB-001/reservations")).andExpect(status().isOk());

        Meter meter = registry.get("http.server.requests").tag("uri", ROUTE).tag("status", "200").timer();
        assertThat(meter).isNotNull();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(ReservationController.class)
    static class TestApp {
    }

    @RestController
    static class ReservationController {

        @PostMapping(ROUTE)
        String reserve(@PathVariable String sku) {
            return sku;
        }
    }
}
