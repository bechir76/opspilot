package dev.opspilot.order.inventory;

/** Inventory could not be reached or answered unexpectedly (5xx, timeout, connection error): a technical failure. */
public class InventoryUnavailableException extends RuntimeException {

    public InventoryUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
