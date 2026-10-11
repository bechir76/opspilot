package dev.opspilot.order.inventory;

import java.time.Duration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Connection settings for inventory-service. Timeouts are explicit: the JDK default is to wait forever,
 * which turns a slow dependency into exhausted request threads.
 */
@Validated
@ConfigurationProperties(prefix = "opspilot.inventory")
public record InventoryProperties(
        @NotBlank String baseUrl,
        @NotNull @DefaultValue("1s") Duration connectTimeout,
        @NotNull @DefaultValue("2s") Duration readTimeout) {
}
