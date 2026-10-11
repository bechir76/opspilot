package dev.opspilot.inventory.api;

import java.util.List;

import dev.opspilot.inventory.product.InventoryService;
import dev.opspilot.inventory.product.Reservation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final InventoryService inventory;

    public ProductController(InventoryService inventory) {
        this.inventory = inventory;
    }

    @GetMapping
    public List<ProductResponse> list() {
        return inventory.listProducts().stream().map(ProductResponse::from).toList();
    }

    @GetMapping("/{sku}")
    public ProductResponse get(@PathVariable String sku) {
        return ProductResponse.from(inventory.getProduct(sku));
    }

    @PostMapping("/{sku}/reservations")
    public Reservation reserve(@PathVariable String sku, @Valid @RequestBody ReservationRequest request) {
        return inventory.reserve(sku, request.quantity());
    }
}
