package com.example.refurbished.warranty;

import com.example.refurbished.inventory.DeviceUnit;
import com.example.refurbished.product.Product;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WarrantyTest {

    @Test
    void calculatesEndDateFromStartAndDuration() {
        LocalDate start = LocalDate.of(2026, 1, 31);

        Warranty warranty = new Warranty(device(), 12, start);

        assertEquals(start, warranty.getStartsOn());
        assertEquals(LocalDate.of(2027, 1, 31), warranty.getEndsOn());
        assertEquals(12, warranty.getDurationMonths());
    }

    @Test
    void rejectsDurationOutsideSupportedRange() {
        assertThrows(IllegalArgumentException.class, () -> new Warranty(device(), 0, LocalDate.now()));
        assertThrows(IllegalArgumentException.class, () -> new Warranty(device(), 37, LocalDate.now()));
    }

    private DeviceUnit device() {
        Product product = new Product("WARRANTY-MODEL", "Model", "Brand", null, true);
        return new DeviceUnit(product, "WARRANTY-SERIAL", null, null, null);
    }
}
