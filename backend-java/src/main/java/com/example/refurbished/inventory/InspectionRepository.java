package com.example.refurbished.inventory;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InspectionRepository extends JpaRepository<Inspection, UUID> {
    List<Inspection> findByDeviceUnitIdOrderByCreatedAtDesc(UUID deviceUnitId);
}
