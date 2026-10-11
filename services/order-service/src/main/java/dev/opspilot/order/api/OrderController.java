package dev.opspilot.order.api;

import java.util.List;

import dev.opspilot.order.order.Order;
import dev.opspilot.order.order.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orders;

    public OrderController(OrderService orders) {
        this.orders = orders;
    }

    /**
     * 201 confirmed, 409 rejected by inventory (client can't fix it by retrying),
     * 502 inventory failed (our upstream is broken, not us and not the client).
     * Rejected and failed orders are still persisted; their id is returned in the problem body.
     */
    @PostMapping
    public ResponseEntity<?> place(@Valid @RequestBody PlaceOrderRequest request) {
        Order order = orders.placeOrder(request.sku(), request.quantity());
        return switch (order.getStatus()) {
            case CONFIRMED -> ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.from(order));
            case REJECTED -> problem(HttpStatus.CONFLICT, "Order rejected", order);
            default -> problem(HttpStatus.BAD_GATEWAY, "Inventory unavailable", order);
        };
    }

    @GetMapping
    public List<OrderResponse> latest(@RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return orders.latestOrders(limit).stream().map(OrderResponse::from).toList();
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String title, Order order) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, order.getFailureReason());
        problem.setTitle(title);
        problem.setProperty("orderId", order.getId());
        problem.setProperty("orderStatus", order.getStatus());
        return ResponseEntity.status(status).body(problem);
    }
}
