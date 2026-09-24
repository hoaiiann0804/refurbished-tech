package com.example.refurbished.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "oauth_login_codes")
public class OAuthLoginCode {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "code_hash", nullable = false, unique = true, length = 64, updatable = false)
    private String codeHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private AppUser user;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OAuthLoginCode() {}

    public OAuthLoginCode(String codeHash, AppUser user, Instant expiresAt) {
        this.codeHash = codeHash;
        this.user = user;
        this.expiresAt = expiresAt;
    }

    public void consume(Instant now) {
        if (usedAt != null || !expiresAt.isAfter(now)) {
            throw new IllegalStateException("OAuth login code is invalid or expired.");
        }
        usedAt = now;
    }

    @PrePersist void onCreate() { createdAt = Instant.now(); }
    public AppUser getUser() { return user; }
}
