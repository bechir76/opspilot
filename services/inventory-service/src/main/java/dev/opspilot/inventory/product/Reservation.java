package dev.opspilot.inventory.product;

public record Reservation(String sku, int reserved, int remaining) {
}
