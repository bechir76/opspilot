package dev.opspilot.inventory.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ReservationRequest(@NotNull @Min(1) @Max(100) Integer quantity) {
}
