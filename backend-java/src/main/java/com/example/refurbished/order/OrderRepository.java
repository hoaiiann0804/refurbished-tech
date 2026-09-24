package com.example.refurbished.order;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    @EntityGraph(attributePaths = {"items", "items.deviceUnit"})
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findDetailedById(@Param("id") UUID id);
}
