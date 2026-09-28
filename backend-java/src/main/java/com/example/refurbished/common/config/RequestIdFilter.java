package com.example.refurbished.common.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String supplied = request.getHeader("X-Request-ID");
        // Không đưa dữ liệu tùy ý từ client vào log: giới hạn ký tự để tránh chèn dòng log.
        String id = supplied != null && supplied.matches("[A-Za-z0-9._:-]{1,64}") ? supplied : UUID.randomUUID().toString();
        MDC.put("requestId", id);
        response.setHeader("X-Request-ID", id);
        try { chain.doFilter(request, response); } finally { MDC.remove("requestId"); }
    }
}
