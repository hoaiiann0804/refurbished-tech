package com.example.refurbished.order;

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

@Service
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orders;
    private final DeviceUnitRepository devices;

    public OrderService(OrderRepository orders, DeviceUnitRepository devices) {
        this.orders = orders;
        this.devices = devices;
    }

    @Transactional
    public OrderResponse checkout(CheckoutRequest request) {
        List<UUID> deviceIds = new ArrayList<>(request.deviceUnitIds());
        if (new HashSet<>(deviceIds).size() != deviceIds.size()) {
            throw new BusinessConflictException("A device unit may appear only once in a checkout.");
        }

        // A stable locking order reduces deadlock risk when checkouts contain multiple devices.
        deviceIds.sort(Comparator.naturalOrder());
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

        Order order = new Order(request.customerName());
        lockedDevices.forEach(device -> {
            device.reserveForCheckout();
            device.completeSale();
            order.addSoldDevice(device);
        });

        return OrderResponse.from(orders.saveAndFlush(order));
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
