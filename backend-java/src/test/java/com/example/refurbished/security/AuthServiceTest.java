package com.example.refurbished.security;

import com.example.refurbished.audit.AuditService;
import com.example.refurbished.common.exception.BusinessConflictException;
import com.example.refurbished.security.dto.UserResponse;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.FluentQuery;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthServiceTest {

    private InMemoryAppUserRepository users;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        users = new InMemoryAppUserRepository();
        PasswordEncoder passwordEncoder = new PasswordEncoder() {
            @Override public String encode(CharSequence rawPassword) { return "encoded_" + rawPassword; }
            @Override public boolean matches(CharSequence rawPassword, String encodedPassword) {
                return ("encoded_" + rawPassword).equals(encodedPassword);
            }
        };
        AuditService noOpAudit = new AuditService(null, null) {
            @Override public void record(String action, String targetType, UUID targetId, Map<String, ?> metadata) {}
        };
        authService = new AuthService(users, passwordEncoder, null, noOpAudit);
    }

    @Test
    void cannotDisableLastAdmin() {
        AppUser admin = createTestUser("admin@example.com", UserRole.ADMIN, true);
        users.save(admin);

        BusinessConflictException ex = assertThrows(BusinessConflictException.class,
                () -> authService.updateUserStatus(admin.getId(), false));
        assertEquals("Cannot disable the last active administrator.", ex.getMessage());
    }

    @Test
    void cannotDemoteLastAdmin() {
        AppUser admin = createTestUser("admin@example.com", UserRole.ADMIN, true);
        users.save(admin);

        BusinessConflictException ex = assertThrows(BusinessConflictException.class,
                () -> authService.updateUserRole(admin.getId(), UserRole.STAFF));
        assertEquals("Cannot demote the last active administrator.", ex.getMessage());
    }

    @Test
    void canDisableAdminWhenMultipleAdminsExist() {
        AppUser admin1 = createTestUser("admin1@example.com", UserRole.ADMIN, true);
        AppUser admin2 = createTestUser("admin2@example.com", UserRole.ADMIN, true);
        users.save(admin1);
        users.save(admin2);

        long initialTokenVersion = admin1.getTokenVersion();
        UserResponse response = authService.updateUserStatus(admin1.getId(), false);

        assertFalse(response.enabled());
        assertEquals(initialTokenVersion + 1, admin1.getTokenVersion());
    }

    @Test
    void canDemoteAdminWhenMultipleAdminsExist() {
        AppUser admin1 = createTestUser("admin1@example.com", UserRole.ADMIN, true);
        AppUser admin2 = createTestUser("admin2@example.com", UserRole.ADMIN, true);
        users.save(admin1);
        users.save(admin2);

        long initialTokenVersion = admin1.getTokenVersion();
        UserResponse response = authService.updateUserRole(admin1.getId(), UserRole.STAFF);

        assertEquals(UserRole.STAFF, response.role());
        assertEquals(initialTokenVersion + 1, admin1.getTokenVersion());
    }

    @Test
    void canManageStaffWithoutAdminCheck() {
        AppUser staff = createTestUser("staff@example.com", UserRole.STAFF, true);
        users.save(staff);

        UserResponse statusResponse = authService.updateUserStatus(staff.getId(), false);
        assertFalse(statusResponse.enabled());

        UserResponse roleResponse = authService.updateUserRole(staff.getId(), UserRole.ADMIN);
        assertEquals(UserRole.ADMIN, roleResponse.role());
    }

    private AppUser createTestUser(String email, UserRole role, boolean enabled) {
        AppUser user = new AppUser(email, "Name", "hash", role);
        try {
            Field idField = AppUser.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(user, UUID.randomUUID());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        if (!enabled) {
            user.updateStatus(false);
        }
        return user;
    }

    static class InMemoryAppUserRepository implements AppUserRepository {
        private final Map<UUID, AppUser> map = new HashMap<>();

        @Override public Optional<AppUser> findByIdForUpdate(UUID id) { return Optional.ofNullable(map.get(id)); }
        @Override public List<AppUser> findByRoleAndEnabledForUpdate(UserRole role) {
            return map.values().stream().filter(u -> u.getRole() == role && u.isEnabled()).toList();
        }
        @Override public Optional<AppUser> findByEmailForUpdate(String email) { return findByEmail(email); }
        @Override public Optional<AppUser> findByEmail(String email) {
            return map.values().stream().filter(u -> u.getEmail().equalsIgnoreCase(email)).findFirst();
        }
        @Override public boolean existsByEmail(String email) {
            return map.values().stream().anyMatch(u -> u.getEmail().equalsIgnoreCase(email));
        }
        @Override public <S extends AppUser> S save(S entity) {
            map.put(entity.getId(), entity);
            return entity;
        }
        @Override public <S extends AppUser> S saveAndFlush(S entity) { return save(entity); }
        @Override public void flush() {}
        @Override public Optional<AppUser> findById(UUID id) { return Optional.ofNullable(map.get(id)); }
        @Override public List<AppUser> findAll() { return new ArrayList<>(map.values()); }
        @Override public <S extends AppUser> List<S> saveAll(Iterable<S> entities) { throw new UnsupportedOperationException(); }
        @Override public <S extends AppUser> List<S> saveAllAndFlush(Iterable<S> entities) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllInBatch(Iterable<AppUser> entities) {}
        @Override public void deleteAllByIdInBatch(Iterable<UUID> ids) {}
        @Override public void deleteAllInBatch() {}
        @Override public AppUser getOne(UUID id) { return map.get(id); }
        @Override public AppUser getById(UUID id) { return map.get(id); }
        @Override public AppUser getReferenceById(UUID id) { return map.get(id); }
        @Override public <S extends AppUser> Optional<S> findOne(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends AppUser> List<S> findAll(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends AppUser> List<S> findAll(Example<S> example, Sort sort) { throw new UnsupportedOperationException(); }
        @Override public <S extends AppUser> Page<S> findAll(Example<S> example, Pageable pageable) { throw new UnsupportedOperationException(); }
        @Override public <S extends AppUser> long count(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends AppUser> boolean exists(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends AppUser, R> R findBy(Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { throw new UnsupportedOperationException(); }
        @Override public boolean existsById(UUID id) { return map.containsKey(id); }
        @Override public List<AppUser> findAllById(Iterable<UUID> ids) { throw new UnsupportedOperationException(); }
        @Override public long count() { return map.size(); }
        @Override public void deleteById(UUID id) { map.remove(id); }
        @Override public void delete(AppUser entity) { map.remove(entity.getId()); }
        @Override public void deleteAllById(Iterable<? extends UUID> ids) { ids.forEach(map::remove); }
        @Override public void deleteAll(Iterable<? extends AppUser> entities) { entities.forEach(this::delete); }
        @Override public void deleteAll() { map.clear(); }
        @Override public List<AppUser> findAll(Sort sort) { throw new UnsupportedOperationException(); }
        @Override public Page<AppUser> findAll(Pageable pageable) { throw new UnsupportedOperationException(); }
    }
}
