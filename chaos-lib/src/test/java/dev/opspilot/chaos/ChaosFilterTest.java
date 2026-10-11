package dev.opspilot.chaos;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ChaosFilterTest {

    private final ChaosState state = new ChaosState(code -> { });
    private double nextRandom = 0.5;
    private final ChaosFilter filter = new ChaosFilter(state, () -> nextRandom);

    @AfterEach
    void tearDown() {
        state.close();
    }

    @Test
    void passesThroughWhenNoFaults() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(get("/api/products"), response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void passesThroughWhenRandomIsAboveErrorRate() throws Exception {
        state.setErrorRate(0.3);
        nextRandom = 0.3;
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(get("/api/products"), new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void returnsProblemJson500WhenErrorInjected() throws Exception {
        state.setErrorRate(0.3);
        nextRandom = 0.29;
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(get("/api/orders"), response, chain);

        assertThat(chain.getRequest()).as("downstream must not be called").isNull();
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(response.getContentAsString())
                .contains("\"status\":500")
                .contains("\"title\":\"Internal Server Error\"")
                .contains("\"instance\":\"/api/orders\"");
    }

    @Test
    void addsConfiguredLatency() throws Exception {
        state.setLatency(100);
        long start = System.nanoTime();

        filter.doFilter(get("/api/products"), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(Duration.ofMillis(100));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator", "/actuator/health/liveness", "/actuator/prometheus", "/chaos", "/chaos/reset"})
    void neverTouchesActuatorOrChaosPaths(String path) throws Exception {
        state.setErrorRate(1.0);
        state.setLatency(5_000);
        nextRandom = 0.0;
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();
        long start = System.nanoTime();

        filter.doFilter(get(path), response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(1));
    }

    @Test
    void lookalikePathsAreStillAffected() throws Exception {
        state.setErrorRate(1.0);
        nextRandom = 0.0;
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(get("/chaosmonkey"), response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(500);
    }

    private static MockHttpServletRequest get(String path) {
        return new MockHttpServletRequest("GET", path);
    }
}
