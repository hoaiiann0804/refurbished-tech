package com.example.refurbished.order;

import com.example.refurbished.common.exception.BusinessConflictException;
import com.example.refurbished.customer.Customer;
import com.example.refurbished.inventory.ConditionGrade;
import com.example.refurbished.inventory.DeviceStatus;
import com.example.refurbished.inventory.DeviceUnit;
import com.example.refurbished.product.Product;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderTest {

    @Test
    void cancellingOrderChangesStatusAndReleasesDevice() {
        Customer customer = new Customer("0987654321", "Customer A", null);
        Order order = new Order("Customer A", customer);

        Product product = new Product("PROD-1", "MacBook Pro", "Apple", null, true);
        DeviceUnit device = new DeviceUnit(product, "SN-100", ConditionGrade.A, 95, new BigDecimal("25000000.00"));
        device.startInspection();
        device.completeInspection(true, ConditionGrade.A, 95, new BigDecimal("25000000.00"), "Passed inspection.", null);

        device.reserveForCheckout();
        device.completeSale();
        order.addSoldDevice(device);

        assertEquals(OrderStatus.COMPLETED, order.getStatus());
        assertEquals(DeviceStatus.SOLD, device.getStatus());

        order.cancel();
        assertEquals(OrderStatus.CANCELLED, order.getStatus());

        device.returnToInventory();
        assertEquals(DeviceStatus.AVAILABLE, device.getStatus());
    }

    @Test
    void cannotCancelAlreadyCancelledOrder() {
        Order order = new Order("Customer A");
        order.cancel();
        assertEquals(OrderStatus.CANCELLED, order.getStatus());

        assertThrows(BusinessConflictException.class, order::cancel);
    }
}
