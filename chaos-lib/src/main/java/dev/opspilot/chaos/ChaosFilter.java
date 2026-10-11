package dev.opspilot.chaos;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Adds the configured latency to every business request. Errors are injected later, by
 * {@link ChaosErrorInterceptor}, once Spring MVC has matched the route.
 */
public class ChaosFilter extends OncePerRequestFilter {

    private final ChaosState state;

    public ChaosFilter(ChaosState state) {
        this.state = state;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return ChaosPaths.isExempt(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long latency = state.latencyMs();
        if (latency > 0) {
            sleep(latency);
        }
        chain.doFilter(request, response);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
