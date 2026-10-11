package dev.opspilot.chaos;

import java.io.IOException;
import java.net.SocketException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.DoubleSupplier;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Fails a fraction of business requests with HTTP 500.
 * <p>
 * This is an interceptor rather than a servlet filter on purpose: it runs after Spring MVC has matched
 * the request to a route, so {@code http.server.requests} records the injected 500 with the real
 * {@code uri} pattern (e.g. {@code /api/products/{sku}}) exactly like an organic failure, instead of
 * {@code uri="UNKNOWN"}.
 */
public class ChaosErrorInterceptor implements HandlerInterceptor {

    static final String UPSTREAM_ERROR = "Connection reset while reading from upstream resource";

    private static final Logger log = LoggerFactory.getLogger(ChaosErrorInterceptor.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final ChaosState state;
    private final DoubleSupplier random;

    /** @param random source of values in [0, 1); injectable so tests are deterministic. */
    public ChaosErrorInterceptor(ChaosState state, DoubleSupplier random) {
        this.state = state;
        this.random = random;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        // Only controller methods: never static resources, actuator or the chaos API itself.
        if (!(handler instanceof HandlerMethod) || ChaosPaths.isExempt(request)) {
            return true;
        }
        double rate = state.errorRate();
        if (rate > 0 && random.getAsDouble() < rate) {
            // A realistic-looking failure: real exception type, message and stack trace in the logs.
            SocketException cause = new SocketException(UPSTREAM_ERROR);
            log.error("Request processing failed: {} {}", request.getMethod(), request.getRequestURI(), cause);
            writeProblem(request, response);
            return false;
        }
        return true;
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
}
