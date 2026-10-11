package dev.opspilot.inventory.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class InventoryServiceTest {

    private final ProductRepository products = mock(ProductRepository.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final InventoryService service = new InventoryService(products, registry);

    @Test
    void reservesWhenStockIsAvailable() {
        when(products.reserve("KB-001", 3)).thenReturn(1);
        when(products.findBySku("KB-001")).thenReturn(Optional.of(product("KB-001", 7)));

        Reservation reservation = service.reserve("KB-001", 3);

        assertThat(reservation).isEqualTo(new Reservation("KB-001", 3, 7));
        assertThat(count("reserved")).isEqualTo(1.0);
        assertThat(count("insufficient_stock")).isZero();
    }

    @Test
    void rejectsWhenStockIsInsufficient() {
        when(products.reserve("WC-001", 10)).thenReturn(0);
        when(products.findBySku("WC-001")).thenReturn(Optional.of(product("WC-001", 2)));

        assertThatThrownBy(() -> service.reserve("WC-001", 10))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("requested 10, available 2");
        assertThat(count("insufficient_stock")).isEqualTo(1.0);
        assertThat(count("reserved")).isZero();
    }

    @Test
    void unknownSkuOnReserveIsNotFound() {
        when(products.reserve("NOPE", 1)).thenReturn(0);
        when(products.findBySku("NOPE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reserve("NOPE", 1)).isInstanceOf(ProductNotFoundException.class);
        assertThat(count("reserved")).isZero();
        assertThat(count("insufficient_stock")).isZero();
    }

    @Test
    void getProductThrowsWhenUnknown() {
        when(products.findBySku("NOPE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getProduct("NOPE")).isInstanceOf(ProductNotFoundException.class);
    }

    private double count(String result) {
        return registry.get("inventory.reservations").tag("result", result).counter().count();
    }

    private static Product product(String sku, int stock) {
        return new Product(sku, "Test product", new BigDecimal("10.00"), stock);
    }
}
