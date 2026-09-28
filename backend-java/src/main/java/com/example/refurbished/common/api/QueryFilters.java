package com.example.refurbished.common.api;

import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class QueryFilters {
    private QueryFilters() {}

    public static void validateWindow(Instant from, Instant to) {
        // Khoảng [from,to) giúp hai ngày liên tiếp không đếm trùng đơn đúng nửa đêm.
        if (from != null && to != null && !from.isBefore(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must be before to.");
        }
    }

    public static String literalContains(String text) {
        // Người dùng tìm ký tự %/_ thật, không được vô tình biến chúng thành wildcard SQL.
        return "%" + text.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }
}
