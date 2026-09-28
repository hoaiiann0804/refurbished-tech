package com.example.refurbished.inventory;

import com.example.refurbished.common.exception.BusinessConflictException;
import com.example.refurbished.common.exception.InvalidInspectionException;
import com.example.refurbished.product.Product;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DeviceUnitTest {

    @Test
    void validLifecycleReachesAvailable() {
        DeviceUnit device = receivedDevice();

        device.startInspection();
        device.completeInspection(true, ConditionGrade.A, 91,
                new BigDecimal("12500000.00"), "All required checks passed.", null);

        assertEquals(DeviceStatus.AVAILABLE, device.getStatus());
        assertEquals(Boolean.TRUE, device.getInspectionPassed());
        assertEquals(ConditionGrade.A, device.getGrade());
        assertEquals(91, device.getBatteryHealth());
        assertNull(device.getBatteryHealthUnavailableReason());
    }

    @Test
    void failedInspectionEndsInRejected() {
        DeviceUnit device = receivedDevice();

        device.startInspection();
        device.completeInspection(false, null, null, null,
                "Mainboard diagnostics failed.", null);

        assertEquals(DeviceStatus.REJECTED, device.getStatus());
        assertEquals(Boolean.FALSE, device.getInspectionPassed());
    }

    @Test
    void cannotSkipInspectingOrRepeatCompletedInspection() {
        DeviceUnit device = receivedDevice();
        assertThrows(BusinessConflictException.class, () -> device.completeInspection(
                true, ConditionGrade.A, 90, BigDecimal.TEN, "Passed.", null));

        device.startInspection();
        assertThrows(BusinessConflictException.class, device::startInspection);
        device.completeInspection(false, null, null, null, "Rejected.", null);
        assertThrows(BusinessConflictException.class, () -> device.completeInspection(
                false, null, null, null, "Rejected again.", null));
    }

    @Test
    void invalidPassingEvidenceDoesNotPartiallyMutateDevice() {
        DeviceUnit device = receivedDevice();
        device.startInspection();

        assertThrows(InvalidInspectionException.class, () -> device.completeInspection(
                true, ConditionGrade.A, null, new BigDecimal("100.00"), "Passed.", null));

        assertEquals(DeviceStatus.INSPECTING, device.getStatus());
        assertNull(device.getInspectionPassed());
        assertNull(device.getInspectedAt());
    }

    @Test
    void batteryReasonIsAcceptedOnlyWhenMeasurementIsUnavailable() {
        DeviceUnit device = receivedDevice();
        device.startInspection();
        assertThrows(InvalidInspectionException.class, () -> device.completeInspection(
                true, ConditionGrade.B, 85, new BigDecimal("100.00"),
                "Passed.", "Battery cannot be measured."));

        device.completeInspection(true, ConditionGrade.B, null, new BigDecimal("100.00"),
                "Passed with unavailable battery metric.", "Desktop device without a battery.");
        assertEquals(DeviceStatus.AVAILABLE, device.getStatus());
        assertEquals("Desktop device without a battery.", device.getBatteryHealthUnavailableReason());
    }

    @Test
    void saleRequiresAvailableThenReservedLifecycle() {
        DeviceUnit device = receivedDevice();
        device.startInspection();
        device.completeInspection(true, ConditionGrade.A, 91, new BigDecimal("100.00"),
                "Passed.", null);

        assertThrows(BusinessConflictException.class, device::completeSale);
        device.reserveForCheckout();
        assertEquals(DeviceStatus.RESERVED, device.getStatus());
        device.completeSale();
        assertEquals(DeviceStatus.SOLD, device.getStatus());
        assertThrows(BusinessConflictException.class, device::reserveForCheckout);
    }

    @Test
    void repairWorkflowTransitionsRejectedThroughInRepairToAvailable() {
        DeviceUnit device = receivedDevice();
        device.startInspection();
        device.completeInspection(false, null, null, null, "Defective screen.", null);
        assertEquals(DeviceStatus.REJECTED, device.getStatus());

        device.sendToRepair();
        assertEquals(DeviceStatus.IN_REPAIR, device.getStatus());

        device.startInspection();
        assertEquals(DeviceStatus.INSPECTING, device.getStatus());

        device.completeInspection(true, ConditionGrade.B, 88, new BigDecimal("10500000.00"),
                "Screen replaced and re-calibrated successfully.", null);
        assertEquals(DeviceStatus.AVAILABLE, device.getStatus());
        assertEquals(ConditionGrade.B, device.getGrade());
        assertEquals(88, device.getBatteryHealth());
    }

    @Test
    void cannotSendToRepairFromNonRejectedStatus() {
        DeviceUnit device = receivedDevice();
        assertThrows(BusinessConflictException.class, device::sendToRepair);

        device.startInspection();
        assertThrows(BusinessConflictException.class, device::sendToRepair);

        device.completeInspection(true, ConditionGrade.A, 95, new BigDecimal("15000000.00"),
                "Passed.", null);
        assertThrows(BusinessConflictException.class, device::sendToRepair);
    }

    private DeviceUnit receivedDevice() {
        Product product = new Product("MODEL-1", "Model", "Brand", null, true);
        return new DeviceUnit(product, "SERIAL-1", null, null, null);
    }
}
