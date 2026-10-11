package dev.opspilot.order.order;

public enum OrderStatus {
    /** Created, inventory not yet called. */
    PENDING,
    /** Stock reserved. */
    CONFIRMED,
    /** Business rejection: unknown product or insufficient stock. */
    REJECTED,
    /** Technical failure: inventory unreachable, timed out or returned an unexpected error. */
    FAILED
}
