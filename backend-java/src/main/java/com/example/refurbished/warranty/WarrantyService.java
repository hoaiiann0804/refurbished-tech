package com.example.refurbished.warranty;

import com.example.refurbished.audit.AuditService;
import java.util.Map;

import com.example.refurbished.common.exception.BusinessConflictException;
import com.example.refurbished.common.exception.ResourceNotFoundException;
import com.example.refurbished.inventory.DeviceStatus;
import com.example.refurbished.inventory.DeviceUnit;
import com.example.refurbished.inventory.DeviceUnitRepository;
import com.example.refurbished.warranty.dto.CreateWarrantyRequest;
import com.example.refurbished.warranty.dto.WarrantyResponse;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class WarrantyService {

    private final WarrantyRepository warranties;
    private final DeviceUnitRepository devices;
    private final AuditService audit;

    public WarrantyService(WarrantyRepository warranties, DeviceUnitRepository devices, AuditService audit) {
        this.warranties = warranties;
        this.devices = devices;
        this.audit = audit;
    }

    @Transactional
    public WarrantyResponse issue(CreateWarrantyRequest request) {
        DeviceUnit device = devices.findByIdForUpdate(request.deviceUnitId())
                .orElseThrow(() -> new ResourceNotFoundException("Device unit not found."));
        if (device.getStatus() != DeviceStatus.SOLD) {
            throw new BusinessConflictException("Warranty can be issued only for a sold device unit.");
        }
        if (warranties.existsByDeviceUnitId(device.getId())) {
            throw new BusinessConflictException("A warranty already exists for this device unit.");
        }

        Warranty warranty = new Warranty(device, request.durationMonths(), LocalDate.now(ZoneOffset.UTC));
        warranties.saveAndFlush(warranty);
        audit.record("WARRANTY_ISSUED", "DEVICE_UNIT", device.getId(), Map.of("warrantyId", warranty.getId(), "durationMonths", request.durationMonths()));
        return WarrantyResponse.from(warranty);
    }

    public WarrantyResponse get(UUID id) {
        return WarrantyResponse.from(warranties.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Warranty not found.")));
    }

    public WarrantyResponse getByDeviceUnit(UUID deviceUnitId) {
        return WarrantyResponse.from(warranties.findByDeviceUnitId(deviceUnitId)
                .orElseThrow(() -> new ResourceNotFoundException("Warranty not found for device unit.")));
    }
}
