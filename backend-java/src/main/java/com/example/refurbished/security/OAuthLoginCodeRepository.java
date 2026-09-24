package com.example.refurbished.security;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface OAuthLoginCodeRepository extends JpaRepository<OAuthLoginCode, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OAuthLoginCode> findByCodeHash(String codeHash);
}
