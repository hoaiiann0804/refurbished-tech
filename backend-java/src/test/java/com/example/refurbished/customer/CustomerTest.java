package com.example.refurbished.customer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CustomerTest {

    @Test
    void validCustomerCreationNormalizesData() {
        Customer customer = new Customer("0912345678", "  Nguyen Van A  ", "  Test@Example.COM ");
        assertEquals("0912345678", customer.getPhoneNumber());
        assertEquals("Nguyen Van A", customer.getFullName());
        assertEquals("test@example.com", customer.getEmail());
    }

    @Test
    void phoneValidationRejectsInvalidFormats() {
        assertThrows(IllegalArgumentException.class, () -> new Customer("123", "Name", null));
        assertThrows(IllegalArgumentException.class, () -> new Customer("abc09123456", "Name", null));
        assertThrows(IllegalArgumentException.class, () -> new Customer("", "Name", null));
        assertThrows(IllegalArgumentException.class, () -> new Customer(null, "Name", null));
    }

    @Test
    void nullOrBlankEmailIsStoredAsNull() {
        Customer customer1 = new Customer("0912345678", "Name", null);
        assertNull(customer1.getEmail());

        Customer customer2 = new Customer("0912345678", "Name", "   ");
        assertNull(customer2.getEmail());
    }

    @Test
    void fullNameValidationRejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> new Customer("0912345678", "", null));
        assertThrows(IllegalArgumentException.class, () -> new Customer("0912345678", "   ", null));
        assertThrows(IllegalArgumentException.class, () -> new Customer("0912345678", null, null));
    }
}
