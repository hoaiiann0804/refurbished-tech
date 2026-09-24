package com.example.refurbished.warranty;

import com.example.refurbished.warranty.dto.CreateWarrantyRequest;
import com.example.refurbished.warranty.dto.WarrantyResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/warranties")
public class WarrantyController {

    private final WarrantyService service;

    public WarrantyController(WarrantyService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<WarrantyResponse> issue(@Valid @RequestBody CreateWarrantyRequest request) {
        WarrantyResponse warranty = service.issue(request);
        return ResponseEntity.created(URI.create("/api/warranties/" + warranty.id())).body(warranty);
    }

    @GetMapping("/{id}")
    public WarrantyResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping("/device-unit/{deviceUnitId}")
    public WarrantyResponse getByDeviceUnit(@PathVariable UUID deviceUnitId) {
        return service.getByDeviceUnit(deviceUnitId);
    }
}
