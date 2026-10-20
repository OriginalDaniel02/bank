package danbank.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import danbank.common.dto.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple in-memory, per-IP, fixed-window rate limiter for the unauthenticated
 * endpoints most worth protecting from brute-forcing or scripted signups
 * (login, refresh, signup). A single-instance, in-memory counter is enough for
 * a learning project; a multi-instance deployment would need a shared store
 * (e.g. Redis) instead of this map.
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final int requestsPerMinute;

    public RateLimitingFilter(@Value("${bank.rate-limit.requests-per-minute}") int requestsPerMinute) {
        this.requestsPerMinute = requestsPerMinute;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.startsWith("/api/v1/auth/") || path.equals("/api/v1/users/signUp"));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain
    ) throws ServletException, IOException {
        String clientIp = request.getRemoteAddr();
        Window window = windows.computeIfAbsent(clientIp, ignored -> new Window());

        if (window.tryConsume(requestsPerMinute)) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(
                ApiResponse.error("Too many requests. Please try again in a minute.")
        ));
    }

    private static final class Window {
        private volatile long windowStartEpochSecond = Instant.now().getEpochSecond();
        private final AtomicInteger count = new AtomicInteger(0);

        synchronized boolean tryConsume(int limit) {
            long now = Instant.now().getEpochSecond();
            if (now - windowStartEpochSecond >= 60) {
                windowStartEpochSecond = now;
                count.set(0);
            }
            return count.incrementAndGet() <= limit;
        }
    }
}
