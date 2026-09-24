package com.example.refurbished.security;

import com.example.refurbished.common.exception.AuthenticationFailedException;
import com.example.refurbished.security.dto.TokenResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OAuthCodeService {
    private final OAuthLoginCodeRepository codes;
    private final JwtService tokens;
    private final SecureRandom random = new SecureRandom();

    public OAuthCodeService(OAuthLoginCodeRepository codes, JwtService tokens) {
        this.codes = codes;
        this.tokens = tokens;
    }

    @Transactional
    public String issue(AppUser user) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        codes.saveAndFlush(new OAuthLoginCode(hash(raw), user, Instant.now().plusSeconds(120)));
        return raw;
    }

    @Transactional
    public TokenResponse exchange(String rawCode) {
        OAuthLoginCode code = codes.findByCodeHash(hash(rawCode))
                .orElseThrow(() -> new AuthenticationFailedException("OAuth login code is invalid or expired."));
        try {
            code.consume(Instant.now());
        } catch (IllegalStateException exception) {
            throw new AuthenticationFailedException(exception.getMessage());
        }
        return tokens.issue(code.getUser());
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }
}
