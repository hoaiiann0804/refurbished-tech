package com.example.refurbished.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppUserTest {

    @Test
    void disablingUserRevokesSessions() {
        AppUser user = new AppUser("user@example.com", "Test User", "hash", UserRole.STAFF);
        long initialVersion = user.getTokenVersion();

        user.updateStatus(false);

        assertFalse(user.isEnabled());
        assertEquals(initialVersion + 1, user.getTokenVersion());
    }

    @Test
    void enablingDisabledUserRevokesSessions() {
        AppUser user = new AppUser("user@example.com", "Test User", "hash", UserRole.STAFF);
        user.updateStatus(false);
        long versionAfterDisable = user.getTokenVersion();

        user.updateStatus(true);

        assertTrue(user.isEnabled());
        assertEquals(versionAfterDisable + 1, user.getTokenVersion());
    }

    @Test
    void unchangedStatusDoesNotIncrementTokenVersion() {
        AppUser user = new AppUser("user@example.com", "Test User", "hash", UserRole.STAFF);
        long initialVersion = user.getTokenVersion();

        user.updateStatus(true);

        assertTrue(user.isEnabled());
        assertEquals(initialVersion, user.getTokenVersion());
    }

    @Test
    void changingRoleRevokesSessions() {
        AppUser user = new AppUser("user@example.com", "Test User", "hash", UserRole.STAFF);
        long initialVersion = user.getTokenVersion();

        user.updateRole(UserRole.ADMIN);

        assertEquals(UserRole.ADMIN, user.getRole());
        assertEquals(initialVersion + 1, user.getTokenVersion());
    }

    @Test
    void unchangedRoleDoesNotIncrementTokenVersion() {
        AppUser user = new AppUser("user@example.com", "Test User", "hash", UserRole.ADMIN);
        long initialVersion = user.getTokenVersion();

        user.updateRole(UserRole.ADMIN);

        assertEquals(UserRole.ADMIN, user.getRole());
        assertEquals(initialVersion, user.getTokenVersion());
    }

    @Test
    void nullRoleThrowsException() {
        AppUser user = new AppUser("user@example.com", "Test User", "hash", UserRole.STAFF);
        assertThrows(IllegalArgumentException.class, () -> user.updateRole(null));
    }
}
