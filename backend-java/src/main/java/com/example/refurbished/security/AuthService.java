package com.example.refurbished.security;

import com.example.refurbished.common.exception.AuthenticationFailedException;
import com.example.refurbished.common.exception.BusinessConflictException;
import com.example.refurbished.common.exception.ResourceNotFoundException;
import com.example.refurbished.security.dto.CreateUserRequest;
import com.example.refurbished.security.dto.LoginRequest;
import com.example.refurbished.security.dto.TokenResponse;
import com.example.refurbished.security.dto.UserResponse;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuthService {
    private final AppUserRepository users;
    private final PasswordEncoder passwords;
    private final JwtService tokens;

    public AuthService(AppUserRepository users, PasswordEncoder passwords, JwtService tokens) {
        this.users = users;
        this.passwords = passwords;
        this.tokens = tokens;
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
        String email = normalize(request.email());
        if (users.existsByEmail(email)) {
            throw new BusinessConflictException("A user with this email already exists.");
        }
        return UserResponse.from(users.saveAndFlush(new AppUser(email, request.displayName(),
                passwords.encode(request.password()), request.role())));
    }

    public UserResponse get(UUID id) {
        return UserResponse.from(users.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found.")));
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
}
