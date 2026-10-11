package dev.opspilot.chaos;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;

class ChaosErrorInterceptorTest {

    private final ChaosState state = new ChaosState(code -> { });
    private double nextRandom = 0.5;
    private final ChaosErrorInterceptor interceptor = new ChaosErrorInterceptor(state, () -> nextRandom);
    private final HandlerMethod handler = handlerMethod();

    @AfterEach
    void tearDown() {
        state.close();
    }

    @Test
    void passesThroughWhenNoFaults() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(get("/api/products"), response, handler)).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void passesThroughWhenRandomIsAboveErrorRate() throws Exception {
        state.setErrorRate(0.3);
        nextRandom = 0.3;

        assertThat(interceptor.preHandle(get("/api/products"), new MockHttpServletResponse(), handler)).isTrue();
    }

    @Test
    void returnsProblemJson500WhenErrorInjected() throws Exception {
        state.setErrorRate(0.3);
        nextRandom = 0.29;
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = interceptor.preHandle(get("/api/orders"), response, handler);

        assertThat(proceed).as("controller must not be called").isFalse();
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(response.getContentAsString())
                .contains("\"status\":500")
                .contains("\"title\":\"Internal Server Error\"")
                .contains("\"instance\":\"/api/orders\"");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator", "/actuator/health/liveness", "/chaos", "/chaos/reset"})
    void neverTouchesActuatorOrChaosPaths(String path) throws Exception {
        state.setErrorRate(1.0);
        nextRandom = 0.0;
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(get(path), response, handler)).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void lookalikePathsAreStillAffected() throws Exception {
        state.setErrorRate(1.0);
        nextRandom = 0.0;

        assertThat(interceptor.preHandle(get("/chaosmonkey"), new MockHttpServletResponse(), handler)).isFalse();
    }

    @Test
    void ignoresNonControllerHandlers() throws Exception {
        state.setErrorRate(1.0);
        nextRandom = 0.0;

        assertThat(interceptor.preHandle(get("/favicon.ico"), new MockHttpServletResponse(),
                new ResourceHttpRequestHandler())).isTrue();
    }

    private static MockHttpServletRequest get(String path) {
        return new MockHttpServletRequest("GET", path);
    }

    private static HandlerMethod handlerMethod() {
        try {
            return new HandlerMethod(new Object(), Object.class.getMethod("toString"));
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }
}
