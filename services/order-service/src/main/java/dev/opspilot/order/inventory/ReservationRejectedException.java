package dev.opspilot.order.inventory;

/** Inventory said no (unknown product or insufficient stock): a business failure, not an incident. */
public class ReservationRejectedException extends RuntimeException {

    public ReservationRejectedException(String message) {
        super(message);
    }
}
