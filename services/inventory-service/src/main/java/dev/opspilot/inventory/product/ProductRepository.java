package dev.opspilot.inventory.product;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findBySku(String sku);

    /**
     * Decrements stock only if enough is available, in a single statement. The database evaluates the
     * condition and the update atomically under the row lock, so concurrent reservations can never
     * oversell, without SELECT ... FOR UPDATE or optimistic-lock retries.
     *
     * @return number of rows updated: 1 if reserved, 0 if the SKU is unknown or stock is insufficient
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Product p SET p.stock = p.stock - :quantity WHERE p.sku = :sku AND p.stock >= :quantity")
    int reserve(@Param("sku") String sku, @Param("quantity") int quantity);
}
