package dev.opspilot.chaos;

import java.io.IOException;
import java.net.SocketException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.DoubleSupplier;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Applies request-level faults (latency, then random HTTP 500s) to business endpoints.
 * Never touches {@code /actuator/**} (probes and metrics must keep working) or {@code /chaos/**}
 * (we must always be able to reset).
 */
public class ChaosFilter extends OncePerRequestFilter {

    static final String UPSTREAM_ERROR = "Connection reset while reading from upstream resource";

    private static final Logger log = LoggerFactory.getLogger(ChaosFilter.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final ChaosState state;
    private final DoubleSupplier random;

    /** @param random source of values in [0, 1); injectable so tests are deterministic. */
    public ChaosFilter(ChaosState state, DoubleSupplier random) {
        this.state = state;
        this.random = random;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return isUnder(path, "/actuator") || isUnder(path, "/chaos");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long latency = state.latencyMs();
        if (latency > 0) {
            sleep(latency);
        }

        double rate = state.errorRate();
        if (rate > 0 && random.getAsDouble() < rate) {
            // A realistic-looking failure: real exception type, message and stack trace in the logs.
            SocketException cause = new SocketException(UPSTREAM_ERROR);
            log.error("Request processing failed: {} {}", request.getMethod(), request.getRequestURI(), cause);
            writeProblem(request, response);
            return;
        }

        chain.doFilter(request, response);
    }

    private static void writeProblem(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("type", "about:blank");
        problem.put("title", status.getReasonPhrase());
        problem.put("status", status.value());
        problem.put("detail", "An unexpected error occurred while processing the request");
        problem.put("instance", request.getRequestURI());

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        JSON.writeValue(response.getOutputStream(), problem);
    }

    private static boolean isUnder(String path, String prefix) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
