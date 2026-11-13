package dan.bank.bank.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limits how many sign-in and sign-up requests one address can make per minute, to slow down guessing across many
 * accounts and sign-up abuse. Kept in memory, so it is per server instance and resets on restart: it complements the
 * per-account lockout in {@link LoginThrottle} and is not a replacement for a proxy-level rate limit in production.
 * It uses the connection's address; behind a proxy, configure the proxy to pass the real client address
 * (server.forward-headers-strategy) or rate-limit at the proxy.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MILLIS = 60_000;
    private static final int CLEANUP_THRESHOLD = 10_000;

    private final int maxPerMinute;
    private final Map<String, Deque<Long>> recent = new ConcurrentHashMap<>();

    public AuthRateLimitFilter(@Value("${bank.security.auth-requests-per-minute:60}") int maxPerMinute) {
        this.maxPerMinute = maxPerMinute;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !"POST".equals(request.getMethod())
                || !(path.equals("/api/v1/auth/login") || path.equals("/api/v1/auth/signup") || path.equals("/api/v1/auth/device/verify"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!allow(request.getRemoteAddr())) {
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"message\":\"Too many requests. Please wait a minute and try again.\","
                    + "\"code\":\"TOO_MANY_REQUESTS\",\"fields\":{}}");
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean allow(String address) {
        long now = System.currentTimeMillis();
        if (recent.size() > CLEANUP_THRESHOLD) recent.values().removeIf(times -> times.isEmpty() || now - times.peekLast() > WINDOW_MILLIS);
        Deque<Long> times = recent.computeIfAbsent(address, key -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && now - times.peekFirst() > WINDOW_MILLIS) times.pollFirst();
            if (times.size() >= maxPerMinute) return false;
            times.addLast(now);
            return true;
        }
    }
}
