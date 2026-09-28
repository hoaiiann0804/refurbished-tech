package com.example.refurbished.security;

import com.example.refurbished.common.exception.BusinessConflictException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PasswordPolicyTest {
    @Test void acceptsBoundaryPasswords() {
        assertDoesNotThrow(() -> PasswordPolicy.validate("a".repeat(12)));
        assertDoesNotThrow(() -> PasswordPolicy.validate("a".repeat(72)));
        assertDoesNotThrow(() -> PasswordPolicy.validate("ậ".repeat(24)));
    }

    @Test void rejectsShortBlankAndOversizedUtf8Passwords() {
        for (String invalid : new String[]{"short", " ".repeat(12), "a".repeat(73), "ậ".repeat(25)}) {
            assertThrows(BusinessConflictException.class, () -> PasswordPolicy.validate(invalid));
        }
        assertThrows(BusinessConflictException.class, () -> PasswordPolicy.validate(null));
    }
}
