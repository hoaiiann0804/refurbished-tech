package com.example.refurbished.warranty;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WarrantyRepository extends JpaRepository<Warranty, UUID> {
    boolean existsByDeviceUnitId(UUID deviceUnitId);
    Optional<Warranty> findByDeviceUnitId(UUID deviceUnitId);
}
