package dev.opspilot.order.order;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import dev.opspilot.order.inventory.InventoryClient;
import dev.opspilot.order.inventory.InventoryUnavailableException;
import dev.opspilot.order.inventory.ReservationRejectedException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orders;
    private final InventoryClient inventory;
    private final Map<OrderStatus, Counter> placed = new EnumMap<>(OrderStatus.class);

    public OrderService(OrderRepository orders, InventoryClient inventory, MeterRegistry registry) {
        this.orders = orders;
        this.inventory = inventory;
        for (OrderStatus status : List.of(OrderStatus.CONFIRMED, OrderStatus.REJECTED, OrderStatus.FAILED)) {
            placed.put(status, Counter.builder("orders.placed")
                    .description("Orders placed by final status")
                    .tag("status", status.name().toLowerCase(Locale.ROOT))
                    .register(registry));
        }
    }

    /**
     * Deliberately not {@code @Transactional}: the remote call must not hold a database connection and
     * transaction open for up to the inventory read timeout. Only the final save is transactional.
     */
    public Order placeOrder(String sku, int quantity) {
        Order order = new Order(sku, quantity);
        InventoryUnavailableException technicalFailure = null;
        try {
            inventory.reserve(sku, quantity);
            order.confirm();
        } catch (ReservationRejectedException e) {
            order.reject(e.getMessage());
        } catch (InventoryUnavailableException e) {
            order.fail(e.getMessage());
            technicalFailure = e;
        }

        Order saved = orders.save(order);
        placed.get(saved.getStatus()).increment();
        switch (saved.getStatus()) {
            case CONFIRMED -> log.info("Order confirmed: id={} sku={} quantity={}", saved.getId(), sku, quantity);
            // A business outcome, not an incident: INFO, not ERROR.
            case REJECTED -> log.info("Order rejected: id={} sku={} quantity={} reason={}",
                    saved.getId(), sku, quantity, saved.getFailureReason());
            default -> log.error("Order failed: id={} sku={} quantity={}",
                    saved.getId(), sku, quantity, technicalFailure);
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Order> latestOrders(int limit) {
        return orders.findAllByOrderByCreatedAtDesc(Limit.of(limit));
    }
}
