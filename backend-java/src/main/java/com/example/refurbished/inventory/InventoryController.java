package com.example.refurbished.inventory;

import com.example.refurbished.common.api.PageResponse;
import com.example.refurbished.inventory.dto.CreateDeviceUnitRequest;
import com.example.refurbished.inventory.dto.CompleteInspectionRequest;
import com.example.refurbished.inventory.dto.DeviceUnitResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/device-units")
public class InventoryController {

    private final InventoryService service;

    public InventoryController(InventoryService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<DeviceUnitResponse> receive(@Valid @RequestBody CreateDeviceUnitRequest request) {
        DeviceUnitResponse device = service.receive(request);
        return ResponseEntity.created(URI.create("/api/device-units/" + device.id())).body(device);
    }

    @GetMapping("/{id}")
    public DeviceUnitResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping("/{id}/start-inspection")
    public DeviceUnitResponse startInspection(@PathVariable UUID id) {
        return service.startInspection(id);
    }

    @PostMapping("/{id}/complete-inspection")
    public DeviceUnitResponse completeInspection(@PathVariable UUID id,
            @Valid @RequestBody CompleteInspectionRequest request) {
        return service.completeInspection(id, request);
    }

    @GetMapping
    public PageResponse<DeviceUnitResponse> list(@RequestParam(required = false) UUID productId,
            @RequestParam(required = false) DeviceStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(productId, status, page, size);
    }
}
