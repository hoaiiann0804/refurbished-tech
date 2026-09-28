package com.example.refurbished.warranty;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WarrantyClaimRepository extends JpaRepository<WarrantyClaim, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from WarrantyClaim c where c.id = :id")
    Optional<WarrantyClaim> findByIdForUpdate(@Param("id") UUID id);

    List<WarrantyClaim> findByWarrantyIdOrderByCreatedAtDesc(UUID warrantyId);

    Page<WarrantyClaim> findByStatus(WarrantyClaimStatus status, Pageable pageable);
}
