package com.campusguard.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

/** Diagnose slow server work without logging tokens, account IDs, query strings or payloads. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class SlowRequestLogFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String id = UUID.randomUUID().toString();
        response.setHeader("X-Request-Id", id);
        long start = System.nanoTime();
        try { chain.doFilter(request, response); }
        finally {
            long ms = (System.nanoTime() - start) / 1_000_000;
            if (ms >= 3000) {
                Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
                LoggerFactory.getLogger(SlowRequestLogFilter.class).warn(
                        "Slow request id={} method={} route={} status={} durationMs={}",
                        id, request.getMethod(), pattern == null ? "unmapped" : pattern, response.getStatus(), ms);
            }
        }
    }
}
