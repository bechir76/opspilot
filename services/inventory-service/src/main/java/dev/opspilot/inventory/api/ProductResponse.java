package dev.opspilot.inventory.api;

import java.math.BigDecimal;

import dev.opspilot.inventory.product.Product;

/** API view of a product; keeps the JPA entity (and its id) out of the HTTP contract. */
public record ProductResponse(String sku, String name, BigDecimal price, int stock) {

    static ProductResponse from(Product product) {
        return new ProductResponse(product.getSku(), product.getName(), product.getPrice(), product.getStock());
    }
}
