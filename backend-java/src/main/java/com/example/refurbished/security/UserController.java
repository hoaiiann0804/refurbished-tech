package com.example.refurbished.security;

import com.example.refurbished.common.api.PageResponse;
import com.example.refurbished.security.dto.CreateUserRequest;
import com.example.refurbished.security.dto.UpdateUserRoleRequest;
import com.example.refurbished.security.dto.UpdateUserStatusRequest;
import com.example.refurbished.security.dto.UserResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final AuthService auth;
    public UserController(AuthService auth) { this.auth = auth; }

    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        UserResponse user = auth.createUser(request);
        return ResponseEntity.created(URI.create("/api/users/" + user.id())).body(user);
    }

    @GetMapping
    public PageResponse<UserResponse> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return auth.listUsers(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @GetMapping("/{id}")
    public UserResponse get(@PathVariable UUID id) {
        return auth.get(id);
    }

    @PutMapping("/{id}/status")
    public UserResponse updateStatus(@PathVariable UUID id,
            @Valid @RequestBody UpdateUserStatusRequest request) {
        return auth.updateUserStatus(id, request.enabled());
    }

    @PutMapping("/{id}/role")
    public UserResponse updateRole(@PathVariable UUID id,
            @Valid @RequestBody UpdateUserRoleRequest request) {
        return auth.updateUserRole(id, request.role());
    }
}
