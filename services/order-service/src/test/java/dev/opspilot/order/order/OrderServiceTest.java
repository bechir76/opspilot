package dev.opspilot.order.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.SocketTimeoutException;

import dev.opspilot.order.inventory.InventoryClient;
import dev.opspilot.order.inventory.InventoryUnavailableException;
import dev.opspilot.order.inventory.ReservationRejectedException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrderServiceTest {

    private final OrderRepository orders = mock(OrderRepository.class);
    private final InventoryClient inventory = mock(InventoryClient.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final OrderService service = new OrderService(orders, inventory, registry);

    @BeforeEach
    void saveReturnsTheEntity() {
        when(orders.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void confirmedWhenInventoryReserves() {
        Order order = service.placeOrder("KB-001", 2);

        verify(inventory).reserve("KB-001", 2);
        verify(orders).save(order);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getFailureReason()).isNull();
        assertThat(order.getCreatedAt()).isNotNull();
        assertThat(count("confirmed")).isEqualTo(1.0);
    }

    @Test
    void rejectedWhenInventorySaysNo() {
        doThrow(new ReservationRejectedException("Insufficient stock for WC-001: requested 5, available 1"))
                .when(inventory).reserve("WC-001", 5);

        Order order = service.placeOrder("WC-001", 5);

        verify(orders).save(order);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(order.getFailureReason()).isEqualTo("Insufficient stock for WC-001: requested 5, available 1");
        assertThat(count("rejected")).isEqualTo(1.0);
        assertThat(count("confirmed")).isZero();
    }

    @Test
    void failedWhenInventoryIsUnavailable() {
        doThrow(new InventoryUnavailableException("I/O error: Read timed out", new SocketTimeoutException()))
                .when(inventory).reserve("KB-001", 1);

        Order order = service.placeOrder("KB-001", 1);

        verify(orders).save(order);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(order.getFailureReason()).isEqualTo("I/O error: Read timed out");
        assertThat(count("failed")).isEqualTo(1.0);
    }

    @Test
    void failureReasonIsTruncatedToColumnSize() {
        doThrow(new InventoryUnavailableException("x".repeat(2000), null)).when(inventory).reserve("KB-001", 1);

        Order order = service.placeOrder("KB-001", 1);

        assertThat(order.getFailureReason()).hasSize(Order.MAX_FAILURE_REASON);
    }

    private double count(String status) {
        return registry.get("orders.placed").tag("status", status).counter().count();
    }
}
