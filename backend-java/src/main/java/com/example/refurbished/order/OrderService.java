package com.example.refurbished.order;

import com.example.refurbished.audit.AuditService;
import com.example.refurbished.common.api.PageResponse;
import com.example.refurbished.common.api.QueryFilters;
import com.example.refurbished.order.dto.OrderSummaryResponse;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import com.example.refurbished.common.exception.BusinessConflictException;
import com.example.refurbished.common.exception.ResourceNotFoundException;
import com.example.refurbished.inventory.DeviceStatus;
import com.example.refurbished.inventory.DeviceUnit;
import com.example.refurbished.inventory.DeviceUnitRepository;
import com.example.refurbished.order.dto.CheckoutRequest;
import com.example.refurbished.order.dto.OrderResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.refurbished.customer.Customer;
import com.example.refurbished.customer.CustomerService;

@Service
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orders;
    private final DeviceUnitRepository devices;
    private final CustomerService customers;
    private final AuditService audit;
    private final CheckoutRequests requests;

    public OrderService(OrderRepository orders, DeviceUnitRepository devices,
            CustomerService customers, AuditService audit, CheckoutRequests requests) {
        this.orders = orders;
        this.devices = devices;
        this.customers = customers;
        this.audit = audit;
        this.requests = requests;
    }

    @Transactional
    public OrderResponse checkout(CheckoutRequest request) {
        return checkout(request, null);
    }

    @Transactional
    public OrderResponse checkout(CheckoutRequest request, String key) {
        List<UUID> deviceIds = new ArrayList<>(request.deviceUnitIds());
        if (new HashSet<>(deviceIds).size() != deviceIds.size()) {
            throw new BusinessConflictException("A device unit may appear only once in a checkout.");
        }

        // A stable locking order reduces deadlock risk when checkouts contain multiple devices.
        deviceIds.sort(Comparator.naturalOrder());
        if (key != null) {
            UUID existing = requests.claim(key, request.customerName(), deviceIds);
            // Replay đơn đã commit, không kiểm tra AVAILABLE lại vì các máy đã SOLD.
            if (existing != null) return get(existing);
        }
        List<DeviceUnit> lockedDevices = deviceIds.stream()
                .map(this::findLockedDevice)
                .toList();

        // Validate the complete checkout before mutating any entity.
        lockedDevices.forEach(device -> {
            if (device.getStatus() != DeviceStatus.AVAILABLE) {
                throw new BusinessConflictException(
                        "Device unit " + device.getId() + " is not available for checkout.");
            }
        });

        Customer customer = (request.phoneNumber() != null && !request.phoneNumber().isBlank())
                ? customers.getOrCreate(request.phoneNumber(), request.customerName(), request.email())
                : null;
        Order order = new Order(request.customerName(), customer);
        lockedDevices.forEach(device -> {
            device.reserveForCheckout();
            device.completeSale();
            order.addSoldDevice(device);
        });

        orders.saveAndFlush(order);
        audit.record("ORDER_CHECKED_OUT", "ORDER", order.getId(), Map.of("deviceUnitIds", deviceIds));
        for (DeviceUnit device : lockedDevices) {
            audit.record("DEVICE_SOLD", "DEVICE_UNIT", device.getId(), Map.of("orderId", order.getId()));
        }
        if (key != null) requests.complete(key, order.getId());
        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse cancel(UUID orderId) {
        Order order = orders.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found."));
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new BusinessConflictException("Order is already cancelled.");
        }
        order.cancel();

        for (OrderItem item : order.getItems()) {
            DeviceUnit device = devices.findByIdForUpdate(item.getDeviceUnit().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Device unit not found."));
            device.returnToInventory();
            audit.record("DEVICE_RESTOCKED", "DEVICE_UNIT", device.getId(), Map.of("orderId", order.getId()));
        }

        orders.flush();
        audit.record("ORDER_CANCELLED", "ORDER", order.getId(), Map.of());
        return OrderResponse.from(order);
    }

    public PageResponse<OrderSummaryResponse> list(
            String customerName, Instant from, Instant to, int page, int size) {
        QueryFilters.validateWindow(from, to);
        Specification<Order> filter = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (customerName != null && !customerName.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("customerName")),
                        QueryFilters.literalContains(
                                customerName.trim().toLowerCase(Locale.ROOT)), '!'));
            }
            if (from != null) predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            if (to != null) predicates.add(cb.lessThan(root.get("createdAt"), to));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        var result = orders.findAll(filter, PageRequest.of(page, size,
                Sort.by("createdAt", "id").descending()));
        return PageResponse.from(
                result.map(OrderSummaryResponse::from));
    }

    public OrderResponse get(UUID id) {
        return OrderResponse.from(orders.findDetailedById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found.")));
    }

    private DeviceUnit findLockedDevice(UUID id) {
        return devices.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Device unit not found: " + id + "."));
    }
}
