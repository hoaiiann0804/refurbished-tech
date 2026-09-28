package com.example.refurbished.online;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/online/reservations")
public class OnlineController {
    private final OnlineService service;
    public OnlineController(OnlineService service) { this.service = service; }
    public record Capabilities(boolean sandboxPayments, int reservationMinutes) {}
    @GetMapping("/capabilities") public Capabilities capabilities() { return new Capabilities(service.sandboxEnabled(),15); }
    public record ReserveRequest(@NotNull UUID requestId, @NotNull UUID deviceUnitId,
            @NotBlank @Size(max=200) String customerName, @NotBlank @Size(max=500) String shippingAddress) {}
    public record PaymentRequest(@NotNull UUID eventId, @NotNull @DecimalMin("0.01") @Digits(integer=12,fraction=2) BigDecimal amount,
            @NotBlank @Pattern(regexp="VND") String currency) {}
    public record ShipmentRequest(@NotBlank @Size(max=100) String trackingNumber,
            @NotBlank @Pattern(regexp="SHIPPED|DELIVERED") String status) {}
    @PostMapping public ResponseEntity<OnlineService.Reservation> reserve(@Valid @RequestBody ReserveRequest request) {
        return ResponseEntity.status(201).body(service.reserve(request));
    }
    @GetMapping public com.example.refurbished.common.api.PageResponse<OnlineService.Reservation> list(@RequestParam(defaultValue="0") @Min(0) int page,
            @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) { return service.list(page, size); }
    @GetMapping("/{id}") public OnlineService.Reservation get(@PathVariable UUID id) { return service.get(id); }
    @PostMapping("/{id}/cancel") public OnlineService.Reservation cancel(@PathVariable UUID id) { return service.cancel(id); }
    @PostMapping("/{id}/sandbox-payment") public OnlineService.Reservation pay(@PathVariable UUID id, @Valid @RequestBody PaymentRequest request) {
        return service.pay(id, request);
    }
    @PostMapping("/{id}/sandbox-refund") public OnlineService.Reservation refund(@PathVariable UUID id, @Valid @RequestBody PaymentRequest request) {
        return service.refund(id, request);
    }
    @PostMapping("/{id}/shipment") public OnlineService.Reservation ship(@PathVariable UUID id, @Valid @RequestBody ShipmentRequest request) {
        return service.ship(id, request);
    }
}
