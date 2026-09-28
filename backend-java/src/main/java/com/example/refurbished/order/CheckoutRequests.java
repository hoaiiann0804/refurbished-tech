package com.example.refurbished.order;

import com.example.refurbished.audit.AuditService;
import com.example.refurbished.common.exception.AuthenticationFailedException;
import com.example.refurbished.common.exception.BusinessConflictException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CheckoutRequests {
    private final JdbcTemplate jdbc;
    public CheckoutRequests(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID claim(String key, String customerName, List<UUID> sortedIds) {
        UUID actor = requireActor();
        String hash = hash(customerName.trim() + "\n" + sortedIds);
        // INSERT ON CONFLICT chờ transaction đang giữ cùng key. Sau commit, request
        // thứ hai đọc lại kết quả; nếu request đầu rollback, request sau được quyền làm.
        // Không bắt unique violation vì PostgreSQL đã đánh dấu transaction đó thất bại.
        jdbc.update("INSERT INTO checkout_requests(actor_id,request_key,request_hash,created_at) "
                + "VALUES (?,?,?,CURRENT_TIMESTAMP) ON CONFLICT DO NOTHING", actor, key, hash);
        var record = jdbc.queryForMap("SELECT request_hash,order_id FROM checkout_requests "
                + "WHERE actor_id=? AND request_key=? FOR UPDATE", actor, key);
        if (!hash.equals(record.get("request_hash"))) {
            throw new BusinessConflictException("Idempotency-Key was already used for a different checkout.");
        }
        return (UUID) record.get("order_id");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void complete(String key, UUID orderId) {
        jdbc.update("UPDATE checkout_requests SET order_id=? WHERE actor_id=? AND request_key=?",
                orderId, requireActor(), key);
    }

    @Transactional
    public int cleanupOldRequests(int retentionDays) {
        java.time.OffsetDateTime cutoff = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).minusDays(retentionDays);
        return jdbc.update("DELETE FROM checkout_requests WHERE created_at < ?", cutoff);
    }

    private UUID requireActor() {
        UUID actor = AuditService.currentActorId();
        if (actor == null) throw new AuthenticationFailedException("Idempotency-Key requires a Bearer session.");
        return actor;
    }

    private String hash(String canonicalRequest) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonicalRequest.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
}
