package com.example.refurbished.security;

import com.example.refurbished.audit.AuditService;
import java.util.Map;

import com.example.refurbished.common.exception.AuthenticationFailedException;
import com.example.refurbished.common.exception.BusinessConflictException;
import com.example.refurbished.common.exception.ResourceNotFoundException;
import com.example.refurbished.security.dto.CreateUserRequest;
import com.example.refurbished.security.dto.ChangePasswordRequest;
import com.example.refurbished.security.dto.LoginRequest;
import com.example.refurbished.security.dto.TokenResponse;
import com.example.refurbished.security.dto.UserResponse;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.refurbished.common.api.PageResponse;
import org.springframework.data.domain.Pageable;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class AuthService {
    private final AppUserRepository users;
    private final PasswordEncoder passwords;
    private final JwtService tokens;
    private final AuditService audit;

    public AuthService(AppUserRepository users, PasswordEncoder passwords, JwtService tokens, AuditService audit) {
        this.users = users;
        this.passwords = passwords;
        this.tokens = tokens;
        this.audit = audit;
    }

    public TokenResponse login(LoginRequest request) {
        AppUser user = users.findByEmail(normalize(request.email())).orElse(null);
        if (user == null || !user.isEnabled() || !passwords.matches(request.password(), user.getPasswordHash())) {
            throw new AuthenticationFailedException("Invalid email or password.");
        }
        return tokens.issue(user);
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        PasswordPolicy.validate(request.password());
        String email = normalize(request.email());
        if (users.existsByEmail(email)) {
            throw new BusinessConflictException("A user with this email already exists.");
        }
        AppUser user = users.saveAndFlush(new AppUser(email, request.displayName(),
                passwords.encode(request.password()), request.role()));
        audit.record("USER_CREATED", "USER", user.getId(), Map.of("role", user.getRole()));
        return UserResponse.from(user);
    }

    public PageResponse<UserResponse> listUsers(Pageable pageable) {
        return PageResponse.from(users.findAll(pageable).map(UserResponse::from));
    }

    public UserResponse get(UUID id) {
        return UserResponse.from(users.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found.")));
    }

    @Transactional
    public UserResponse updateUserStatus(UUID id, boolean enabled) {
        AppUser target = users.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));

        if (!enabled && target.getRole() == UserRole.ADMIN && target.isEnabled()) {
            List<AppUser> activeAdmins = users.findByRoleAndEnabledForUpdate(UserRole.ADMIN);
            if (activeAdmins.size() <= 1) {
                throw new BusinessConflictException("Cannot disable the last active administrator.");
            }
        }

        target.updateStatus(enabled);
        users.flush();
        audit.record("USER_STATUS_UPDATED", "USER", id, Map.of("enabled", enabled));
        return UserResponse.from(target);
    }

    @Transactional
    public UserResponse updateUserRole(UUID id, UserRole newRole) {
        AppUser target = users.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));

        if (target.getRole() == UserRole.ADMIN && newRole != UserRole.ADMIN && target.isEnabled()) {
            List<AppUser> activeAdmins = users.findByRoleAndEnabledForUpdate(UserRole.ADMIN);
            if (activeAdmins.size() <= 1) {
                throw new BusinessConflictException("Cannot demote the last active administrator.");
            }
        }

        target.updateRole(newRole);
        users.flush();
        audit.record("USER_ROLE_UPDATED", "USER", id, Map.of("role", newRole.name()));
        return UserResponse.from(target);
    }

    public AppUser requireEnabledByEmail(String email) {
        AppUser user = users.findByEmail(normalize(email))
                .orElseThrow(() -> new AuthenticationFailedException("Google account is not provisioned."));
        if (!user.isEnabled()) {
            throw new AuthenticationFailedException("User account is disabled.");
        }
        return user;
    }

    private String normalize(String email) { return email.trim().toLowerCase(Locale.ROOT); }

    @Transactional
    public void changePassword(UUID id, long tokenVersion,
            ChangePasswordRequest request) {
        AppUser user = requireCurrentSession(id, tokenVersion);
        if (!passwords.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new AuthenticationFailedException("Current password is incorrect.");
        }
        PasswordPolicy.validate(request.newPassword());
        if (passwords.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BusinessConflictException("New password must differ from the current password.");
        }
        user.changePassword(passwords.encode(request.newPassword()));
        users.flush();
        audit.record("PASSWORD_CHANGED", "USER", id, Map.of());
    }

    @Transactional
    public void logoutAll(UUID id, long tokenVersion) {
        requireCurrentSession(id, tokenVersion).revokeSessions();
        users.flush();
        audit.record("SESSIONS_REVOKED", "USER", id, Map.of());
    }

    private AppUser requireCurrentSession(UUID id, long tokenVersion) {
        AppUser user = users.findByIdForUpdate(id)
                .orElseThrow(() -> new AuthenticationFailedException("Account is unavailable."));
        // Kiểm tra lại dưới row lock: token có thể bị thu hồi sau khi qua filter.
        if (!user.isEnabled() || user.getTokenVersion() != tokenVersion) {
            throw new AuthenticationFailedException("Session is revoked. Please sign in again.");
        }
        return user;
    }
}
