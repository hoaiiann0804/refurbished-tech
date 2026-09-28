package com.example.refurbished.warranty;

import com.example.refurbished.common.exception.BusinessConflictException;
import com.example.refurbished.inventory.ConditionGrade;
import com.example.refurbished.inventory.DeviceUnit;
import com.example.refurbished.product.Product;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarrantyClaimTest {

    @Test
    void claimLifecycleResolvesSuccessfullyWithNotes() {
        Warranty warranty = createTestWarranty(LocalDate.now().minusMonths(1), 12);
        assertFalse(warranty.isExpired());

        WarrantyClaim claim = new WarrantyClaim(warranty, "Screen flickering randomly.");
        assertEquals(WarrantyClaimStatus.OPEN, claim.getStatus());

        claim.updateStatus(WarrantyClaimStatus.DIAGNOSING, null);
        assertEquals(WarrantyClaimStatus.DIAGNOSING, claim.getStatus());

        claim.updateStatus(WarrantyClaimStatus.REPAIRING, null);
        assertEquals(WarrantyClaimStatus.REPAIRING, claim.getStatus());

        claim.updateStatus(WarrantyClaimStatus.RESOLVED, "Display cable replaced under warranty.");
        assertEquals(WarrantyClaimStatus.RESOLVED, claim.getStatus());
        assertEquals("Display cable replaced under warranty.", claim.getResolutionNotes());
    }

    @Test
    void resolvingOrRejectingRequiresResolutionNotes() {
        Warranty warranty = createTestWarranty(LocalDate.now().minusMonths(1), 12);
        WarrantyClaim claim = new WarrantyClaim(warranty, "Battery health degraded rapidly.");

        assertThrows(BusinessConflictException.class,
                () -> claim.updateStatus(WarrantyClaimStatus.RESOLVED, null));
        assertThrows(BusinessConflictException.class,
                () -> claim.updateStatus(WarrantyClaimStatus.RESOLVED, "   "));
        assertThrows(BusinessConflictException.class,
                () -> claim.updateStatus(WarrantyClaimStatus.REJECTED, null));
    }

    @Test
    void closedClaimCannotBeModified() {
        Warranty warranty = createTestWarranty(LocalDate.now().minusMonths(1), 12);
        WarrantyClaim claim = new WarrantyClaim(warranty, "Water damage reported.");

        claim.updateStatus(WarrantyClaimStatus.REJECTED, "Device suffered liquid ingress, not covered.");
        assertEquals(WarrantyClaimStatus.REJECTED, claim.getStatus());

        assertThrows(BusinessConflictException.class,
                () -> claim.updateStatus(WarrantyClaimStatus.OPEN, null));
        assertThrows(BusinessConflictException.class,
                () -> claim.updateStatus(WarrantyClaimStatus.RESOLVED, "Attempted reopen."));
    }

    @Test
    void expiredWarrantyDetected() {
        Warranty expiredWarranty = createTestWarranty(LocalDate.now().minusMonths(14), 12);
        assertTrue(expiredWarranty.isExpired());

        Warranty activeWarranty = createTestWarranty(LocalDate.now().minusMonths(2), 12);
        assertFalse(activeWarranty.isExpired());
    }

    private Warranty createTestWarranty(LocalDate startsOn, int months) {
        Product product = new Product("PROD-W1", "iPhone 13", "Apple", null, true);
        DeviceUnit device = new DeviceUnit(product, "SN-W100", ConditionGrade.A, 90, new BigDecimal("14000000.00"));
        return new Warranty(device, months, startsOn);
    }
}
