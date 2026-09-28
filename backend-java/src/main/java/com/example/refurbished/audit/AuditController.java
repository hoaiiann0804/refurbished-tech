package com.example.refurbished.audit;

import com.example.refurbished.common.api.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit-events")
public class AuditController {
    private final AuditService audit;
    public AuditController(AuditService audit) { this.audit = audit; }

    @GetMapping
    public PageResponse<AuditService.Event> list(@RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) @Size(max = 80) String action,
            @RequestParam(required = false) @Size(max = 40) String targetType,
            @RequestParam(required = false) UUID targetId,
            @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return audit.list(actorId, action, targetType, targetId, from, to, page, size);
    }
}
