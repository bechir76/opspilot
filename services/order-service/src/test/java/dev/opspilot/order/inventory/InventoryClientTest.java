package dev.opspilot.order.inventory;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.SocketTimeoutException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class InventoryClientTest {

    private static final String RESERVATION_URL = "http://inventory/api/products/KB-001/reservations";

    private final RestClient.Builder builder = RestClient.builder().baseUrl("http://inventory");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final InventoryClient client = new InventoryClient(builder.build());

    @AfterEach
    void verifyServer() {
        server.verify();
    }

    @Test
    void successfulReservation() {
        server.expect(requestTo(RESERVATION_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"quantity\": 2}"))
                .andRespond(withSuccess("{\"sku\":\"KB-001\",\"reserved\":2,\"remaining\":98}",
                        MediaType.APPLICATION_JSON));

        assertThatCode(() -> client.reserve("KB-001", 2)).doesNotThrowAnyException();
    }

    @Test
    void conflictIsABusinessRejectionWithInventoryDetail() {
        server.expect(requestTo(RESERVATION_URL))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("{\"status\":409,\"detail\":\"Insufficient stock for KB-001: requested 2, available 1\"}"));

        assertThatThrownBy(() -> client.reserve("KB-001", 2))
                .isInstanceOf(ReservationRejectedException.class)
                .hasMessage("Insufficient stock for KB-001: requested 2, available 1");
    }

    @Test
    void notFoundIsABusinessRejection() {
        server.expect(requestTo(RESERVATION_URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.reserve("KB-001", 2))
                .isInstanceOf(ReservationRejectedException.class)
                .hasMessage("Inventory rejected the reservation with HTTP 404");
    }

    @Test
    void serverErrorIsATechnicalFailure() {
        server.expect(requestTo(RESERVATION_URL)).andRespond(withServerError());

        assertThatThrownBy(() -> client.reserve("KB-001", 2))
                .isInstanceOf(InventoryUnavailableException.class)
                .hasMessage("Inventory returned HTTP 500 for KB-001");
    }

    @Test
    void timeoutIsATechnicalFailure() {
        server.expect(requestTo(RESERVATION_URL)).andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> client.reserve("KB-001", 2))
                .isInstanceOf(InventoryUnavailableException.class)
                .hasMessage("Inventory unreachable for KB-001: SocketTimeoutException: Read timed out")
                .hasRootCauseInstanceOf(SocketTimeoutException.class);
    }
}
