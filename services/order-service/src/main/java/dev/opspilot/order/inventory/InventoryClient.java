package dev.opspilot.order.inventory;

import java.io.IOException;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Calls inventory-service and classifies every outcome:
 * <ul>
 *   <li>404 / 409: {@link ReservationRejectedException}, a business answer from a healthy dependency</li>
 *   <li>anything else (5xx, timeouts, connection errors, unexpected 4xx): {@link InventoryUnavailableException}</li>
 * </ul>
 */
@Component
public class InventoryClient {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final RestClient restClient;

    public InventoryClient(RestClient inventoryRestClient) {
        this.restClient = inventoryRestClient;
    }

    public void reserve(String sku, int quantity) {
        try {
            restClient.post()
                    // URI template (not string concatenation) keeps the metric's uri tag low-cardinality.
                    .uri("/api/products/{sku}/reservations", sku)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("quantity", quantity))
                    .retrieve()
                    .onStatus(InventoryClient::isRejection, (request, response) -> {
                        throw new ReservationRejectedException(rejectionReason(response));
                    })
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new InventoryUnavailableException(failureReason(sku, e), e);
        }
    }

    /**
     * A short, classified reason (stored on the order and returned to the caller) instead of the raw
     * exception message, which embeds the upstream response body. The full chain stays in the ERROR log.
     */
    private static String failureReason(String sku, RestClientException e) {
        if (e instanceof RestClientResponseException response) {
            return "Inventory returned HTTP " + response.getStatusCode().value() + " for " + sku;
        }
        Throwable cause = e.getMostSpecificCause();
        return "Inventory unreachable for " + sku + ": " + cause.getClass().getSimpleName()
                + (cause.getMessage() != null ? ": " + cause.getMessage() : "");
    }

    private static boolean isRejection(HttpStatusCode status) {
        return status.isSameCodeAs(HttpStatus.NOT_FOUND) || status.isSameCodeAs(HttpStatus.CONFLICT);
    }

    /** Uses the RFC 9457 {@code detail} from inventory when present, e.g. "Insufficient stock for ...". */
    private static String rejectionReason(ClientHttpResponse response) throws IOException {
        String fallback = "Inventory rejected the reservation with HTTP " + response.getStatusCode().value();
        try {
            JsonNode detail = JSON.readTree(response.getBody()).get("detail");
            return detail != null && detail.isTextual() ? detail.asText() : fallback;
        } catch (IOException e) {
            return fallback;
        }
    }
}
