package com.example.refurbished.inventory.dto;

import com.example.refurbished.inventory.ConditionGrade;
import com.example.refurbished.inventory.Inspection;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InspectionResponse(
        UUID id,
        UUID deviceUnitId,
        UUID inspectorId,
        boolean passed,
        ConditionGrade grade,
        Integer batteryHealth,
        String batteryHealthUnavailableReason,
        BigDecimal salePrice,
        String notes,
        Instant createdAt
) {
    public static InspectionResponse from(Inspection inspection) {
        return new InspectionResponse(
                inspection.getId(),
                inspection.getDeviceUnit().getId(),
                inspection.getInspectorId(),
                inspection.isPassed(),
                inspection.getGrade(),
                inspection.getBatteryHealth(),
                inspection.getBatteryHealthUnavailableReason(),
                inspection.getSalePrice(),
                inspection.getNotes(),
                inspection.getCreatedAt()
        );
    }
}
