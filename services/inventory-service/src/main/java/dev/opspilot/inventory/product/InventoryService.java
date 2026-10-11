package dev.opspilot.inventory.product;

import java.util.List;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final ProductRepository products;
    private final Counter reserved;
    private final Counter insufficientStock;

    public InventoryService(ProductRepository products, MeterRegistry registry) {
        this.products = products;
        this.reserved = reservationCounter(registry, "reserved");
        this.insufficientStock = reservationCounter(registry, "insufficient_stock");
    }

    @Transactional(readOnly = true)
    public List<Product> listProducts() {
        return products.findAll(Sort.by("sku"));
    }

    @Transactional(readOnly = true)
    public Product getProduct(String sku) {
        return products.findBySku(sku).orElseThrow(() -> new ProductNotFoundException(sku));
    }

    @Transactional
    public Reservation reserve(String sku, int quantity) {
        if (products.reserve(sku, quantity) == 0) {
            // The update matched nothing: find out why, to return 404 vs 409.
            Product product = getProduct(sku);
            insufficientStock.increment();
            log.info("Reservation rejected: sku={} requested={} available={}", sku, quantity, product.getStock());
            throw new InsufficientStockException(sku, quantity, product.getStock());
        }
        int remaining = getProduct(sku).getStock();
        reserved.increment();
        log.info("Reserved stock: sku={} quantity={} remaining={}", sku, quantity, remaining);
        return new Reservation(sku, quantity, remaining);
    }

    private static Counter reservationCounter(MeterRegistry registry, String result) {
        return Counter.builder("inventory.reservations")
                .description("Stock reservation attempts by outcome")
                .tag("result", result)
                .register(registry);
    }
}
