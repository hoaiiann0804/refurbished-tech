package com.example.refurbished.inventory;

import com.example.refurbished.common.api.PageResponse;
import com.example.refurbished.common.exception.BusinessConflictException;
import com.example.refurbished.common.exception.InvalidInspectionException;
import com.example.refurbished.common.exception.ResourceNotFoundException;
import com.example.refurbished.inventory.dto.CreateDeviceUnitRequest;
import com.example.refurbished.inventory.dto.CompleteInspectionRequest;
import com.example.refurbished.inventory.dto.DeviceUnitResponse;
import com.example.refurbished.product.Product;
import com.example.refurbished.product.ProductRepository;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class InventoryService {

    private final DeviceUnitRepository devices;
    private final ProductRepository products;

    public InventoryService(DeviceUnitRepository devices, ProductRepository products) {
        this.devices = devices;
        this.products = products;
    }

    @Transactional
    public DeviceUnitResponse receive(CreateDeviceUnitRequest request) {
        Product product = products.findByIdForUpdate(request.productId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found."));
        if (!product.isActive()) {
            throw new BusinessConflictException("Cannot receive a device for an inactive product.");
        }
        DeviceUnit device = new DeviceUnit(product, request.serialNumber(), request.grade(),
                request.batteryHealth(), request.salePrice());
        return DeviceUnitResponse.from(devices.saveAndFlush(device));
    }

    public DeviceUnitResponse get(UUID id) {
        return DeviceUnitResponse.from(devices.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Device unit not found.")));
    }

    @Transactional
    public DeviceUnitResponse startInspection(UUID id) {
        DeviceUnit device = findLockedDevice(id);
        device.startInspection();
        devices.flush();
        return DeviceUnitResponse.from(device);
    }

    @Transactional
    public DeviceUnitResponse completeInspection(UUID id, CompleteInspectionRequest request) {
        DeviceUnit device = findLockedDevice(id);
        if (request.passed() == null) {
            throw new InvalidInspectionException("Inspection result is required.");
        }
        device.completeInspection(request.passed(), request.grade(), request.batteryHealth(),
                request.salePrice(), request.inspectionNotes(), request.batteryHealthUnavailableReason());
        devices.flush();
        return DeviceUnitResponse.from(device);
    }

    private DeviceUnit findLockedDevice(UUID id) {
        return devices.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Device unit not found."));
    }

    public PageResponse<DeviceUnitResponse> list(UUID productId, DeviceStatus status, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt", "id").descending());
        Page<DeviceUnit> result;
        if (productId != null && status != null) {
            result = devices.findByProductIdAndStatus(productId, status, pageable);
        } else if (productId != null) {
            result = devices.findByProductId(productId, pageable);
        } else if (status != null) {
            result = devices.findByStatus(status, pageable);
        } else {
            result = devices.findAll(pageable);
        }
        return PageResponse.from(result.map(DeviceUnitResponse::from));
    }
}
