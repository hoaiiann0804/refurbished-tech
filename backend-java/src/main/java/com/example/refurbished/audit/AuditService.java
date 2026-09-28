package com.example.refurbished.audit;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.example.refurbished.common.api.PageResponse;
import com.example.refurbished.common.api.QueryFilters;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class AuditService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public AuditService(JdbcTemplate jdbc, ObjectMapper mapper) { this.jdbc = jdbc; this.mapper = mapper; }

    public static UUID currentActorId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication instanceof JwtAuthenticationToken jwt
                ? UUID.fromString(jwt.getToken().getSubject()) : null;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String action, String targetType, UUID targetId, Map<String, ?> details) {
        UUID actor = currentActorId();
        recordAs(actor, actor == null ? "SYSTEM" : "USER", action, targetType, targetId, details);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordAs(UUID actor, String kind, String action, String targetType, UUID targetId, Map<String, ?> details) {
        // Audit thành công phải commit/rollback cùng nghiệp vụ. Không dùng REQUIRES_NEW
        // vì nó có thể lưu một sự kiện thành công cho giao dịch bán đã thất bại.
        // Chỉ caller chọn các trường an toàn; không serialize request/password/JWT vào đây.
        try {
            jdbc.update("INSERT INTO audit_events (id,actor_user_id,actor_kind,action,target_type,target_id,occurred_at,details) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb))",
                    UUID.randomUUID(), actor, kind, action, targetType, targetId,
                    Timestamp.from(Instant.now()), mapper.writeValueAsString(details));
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("Audit details could not be serialized.", exception);
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<Event> list(UUID actorId, String action, String targetType, UUID targetId,
        Instant from, Instant to, int page, int size) {
        // kiểm tra khoảnh thời gian truy vấn hợp lệ: form <= to
        QueryFilters.validateWindow(from, to);

        // Xây dựng điều kiện WHERE động theo filter đầu vào 
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();

        // Thêm các điều kiện filter nếu có dữ liệu 
        if (actorId != null) { where.append(" AND actor_user_id=?"); args.add(actorId); }
        if (action != null) { where.append(" AND action=?"); args.add(action); }
        if (targetType != null) { where.append(" AND target_type=?"); args.add(targetType); }
        if (targetId != null) { where.append(" AND target_id=?"); args.add(targetId); }
        if (from != null) { where.append(" AND occurred_at>=?"); args.add(Timestamp.from(from)); }
        if (to != null) { where.append(" AND occurred_at<?"); args.add(Timestamp.from(to)); }

        // Đếm tổng số envent thỏa filter để phục vụ phân trang 
        long total = jdbc.queryForObject("SELECT count(*) FROM audit_events" + where, Long.class, args.toArray());

        // Thêm tham số phân trang 
        args.add(size);
        args.add((long) page * size);

        // Lây dữ liệu theo trang , sắp xếp mới nhất trước 
        List<Event> items = jdbc.query("SELECT * FROM audit_events" + where
                + " ORDER BY occurred_at DESC, id DESC LIMIT ? OFFSET ?", (rs, row) -> {
                    try {
                        return new Event(rs.getObject("id", UUID.class), rs.getObject("actor_user_id", UUID.class),
                                rs.getString("actor_kind"), rs.getString("action"), rs.getString("target_type"),
                                rs.getObject("target_id", UUID.class), rs.getTimestamp("occurred_at").toInstant(),
                                mapper.readTree(rs.getString("details")));
                    } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
                        throw new IllegalStateException("Stored audit details are invalid.", exception);
                    }
                }, args.toArray());
        return new PageResponse<>(items, page, size, total, (int) ((total + size - 1) / size));
    }

    public record Event(UUID id, UUID actorUserId, String actorKind, String action, String targetType,
            UUID targetId, Instant occurredAt, JsonNode details) {}
}
