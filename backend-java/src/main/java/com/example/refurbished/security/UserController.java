package com.example.refurbished.security;

import com.example.refurbished.security.dto.CreateUserRequest;
import com.example.refurbished.security.dto.UserResponse;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
