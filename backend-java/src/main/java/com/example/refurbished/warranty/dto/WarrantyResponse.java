package com.example.refurbished.warranty.dto;

import com.example.refurbished.warranty.Warranty;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record WarrantyResponse(UUID id, UUID deviceUnitId, String serialNumber,
        int durationMonths, LocalDate startsOn, LocalDate endsOn, Instant createdAt) {

    public static WarrantyResponse from(Warranty warranty) {
        return new WarrantyResponse(warranty.getId(), warranty.getDeviceUnit().getId(),
                warranty.getDeviceUnit().getSerialNumber(), warranty.getDurationMonths(),
                warranty.getStartsOn(), warranty.getEndsOn(), warranty.getCreatedAt());
    }
}
