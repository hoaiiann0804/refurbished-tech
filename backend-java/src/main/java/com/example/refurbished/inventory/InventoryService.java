package com.example.refurbished.inventory;

import com.example.refurbished.audit.AuditService;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;

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

import com.example.refurbished.inventory.dto.InspectionResponse;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class InventoryService {

    private final DeviceUnitRepository devices;
    private final ProductRepository products;
    private final InspectionRepository inspections;
    private final AuditService audit;

    public InventoryService(DeviceUnitRepository devices, ProductRepository products,
            InspectionRepository inspections, AuditService audit) {
        this.devices = devices;
        this.products = products;
        this.inspections = inspections;
        this.audit = audit;
    }

    @Transactional
    public DeviceUnitResponse receive(CreateDeviceUnitRequest request) {
        // Đây là nghiệp vụ nhận thiết bị mới vào kho: khó sản phẩm đang xử lý để tránh xung đột đồng thời
        // Kiểm tra sản phẩm còn hoạt động, tạo DeviceUnit mới, lưu dữ liệu và ghi audit trong cùng 1 transaction
        // Mục tiêu đảm bảo dữ liệu luôn nhất quán: nếu có lỗi ở bất kỳ bước nào thì toàn bộ thao tác sẽ rollback 
        // TRánh trường hợp nhận hàng nửa chừng hoặc tạo device cho product vô hiệu hóa . 
        Product product = products.findByIdForUpdate(request.productId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found."));
        if (!product.isActive()) {
            throw new BusinessConflictException("Cannot receive a device for an inactive product.");
        }
        DeviceUnit device = new DeviceUnit(product, request.serialNumber(), request.grade(),
                request.batteryHealth(), request.salePrice());
        devices.saveAndFlush(device);
        audit.record("DEVICE_RECEIVED", "DEVICE_UNIT", device.getId(), Map.of("productId", product.getId(), "serialNumber", device.getSerialNumber()));
        return DeviceUnitResponse.from(device);
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
        audit.record("INSPECTION_STARTED", "DEVICE_UNIT", id, Map.of("status", device.getStatus()));
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

        Inspection inspection = new Inspection(
                device,
                AuditService.currentActorId(),
                request.passed(),
                request.grade(),
                request.batteryHealth(),
                request.batteryHealthUnavailableReason(),
                request.salePrice(),
                request.inspectionNotes()
        );
        inspections.save(inspection);

        devices.flush();
        audit.record("INSPECTION_COMPLETED", "DEVICE_UNIT", id, Map.of("status", device.getStatus(), "passed", request.passed()));
        return DeviceUnitResponse.from(device);
    }

    @Transactional
    public DeviceUnitResponse sendToRepair(UUID id) {
        DeviceUnit device = findLockedDevice(id);
        device.sendToRepair();
        devices.flush();
        audit.record("DEVICE_SENT_TO_REPAIR", "DEVICE_UNIT", id, Map.of("status", device.getStatus()));
        return DeviceUnitResponse.from(device);
    }

    public List<InspectionResponse> getInspections(UUID id) {
        if (!devices.existsById(id)) {
            throw new ResourceNotFoundException("Device unit not found.");
        }
        return inspections.findByDeviceUnitIdOrderByCreatedAtDesc(id)
                .stream().map(InspectionResponse::from).toList();
    }

    private DeviceUnit findLockedDevice(UUID id) {
        return devices.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Device unit not found."));
    }

    public PageResponse<DeviceUnitResponse> list(UUID productId, DeviceStatus status, int page, int size) {
        return list(productId, status, null, page, size);
    }

    public PageResponse<DeviceUnitResponse> list(UUID productId, DeviceStatus status, String serialNumber, int page, int size) {
        // Serial là định danh chính xác, normalize cùng quy tắc nhập kho; không dùng LIKE.
        if (serialNumber != null) {
            String serial = serialNumber.trim().toUpperCase(Locale.ROOT);
            return PageResponse.from(devices.findAll((root, query, cb) -> {
                var filters = new ArrayList<Predicate>();
                filters.add(cb.equal(root.get("serialNumber"), serial));
                if (productId != null) filters.add(cb.equal(root.get("product").get("id"), productId));
                if (status != null) filters.add(cb.equal(root.get("status"), status));
                return cb.and(filters.toArray(Predicate[]::new));
            }, PageRequest.of(page, size, Sort.by("createdAt", "id").descending())).map(DeviceUnitResponse::from));
        }
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
