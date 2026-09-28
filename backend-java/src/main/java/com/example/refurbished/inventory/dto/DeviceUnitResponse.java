package com.example.refurbished.inventory.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.example.refurbished.inventory.ConditionGrade;
import com.example.refurbished.inventory.DeviceStatus;
import com.example.refurbished.inventory.DeviceUnit;

public record DeviceUnitResponse(UUID id, UUID productId, String serialNumber, DeviceStatus status,
        ConditionGrade grade, Integer batteryHealth, String batteryHealthUnavailableReason,
        BigDecimal salePrice, Boolean inspectionPassed,
        String inspectionNotes, Instant inspectedAt, Instant createdAt, Instant updatedAt) {

// Chuyển từ entity sang DeviceUnit sang DTO để trả về client 
    public static DeviceUnitResponse from(DeviceUnit device) {
        return new DeviceUnitResponse(device.getId(), device.getProduct().getId(), device.getSerialNumber(),
                device.getStatus(), device.getGrade(), device.getBatteryHealth(), device.getBatteryHealthUnavailableReason(), device.getSalePrice(),
                device.getInspectionPassed(), device.getInspectionNotes(), device.getInspectedAt(),
                device.getCreatedAt(), device.getUpdatedAt());
    }
}
