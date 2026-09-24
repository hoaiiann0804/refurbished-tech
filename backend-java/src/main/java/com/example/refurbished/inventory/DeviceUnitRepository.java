package com.example.refurbished.inventory;

import java.util.UUID;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeviceUnitRepository extends JpaRepository<DeviceUnit, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DeviceUnit d where d.id = :id")
    Optional<DeviceUnit> findByIdForUpdate(@Param("id") UUID id);

    Page<DeviceUnit> findByProductId(UUID productId, Pageable pageable);
    Page<DeviceUnit> findByStatus(DeviceStatus status, Pageable pageable);
    Page<DeviceUnit> findByProductIdAndStatus(UUID productId, DeviceStatus status, Pageable pageable);
}
