package dev.opspilot.chaos;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ChaosFilterTest {

    private final ChaosState state = new ChaosState(code -> { });
    private final ChaosFilter filter = new ChaosFilter(state);

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
    void addsConfiguredLatency() throws Exception {
        state.setLatency(100);
        MockFilterChain chain = new MockFilterChain();
        long start = System.nanoTime();

        filter.doFilter(get("/api/products"), new MockHttpServletResponse(), chain);

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(Duration.ofMillis(100));
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void neverInjectsErrorsItself() throws Exception {
        state.setErrorRate(1.0);
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(get("/api/products"), response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator", "/actuator/health/liveness", "/actuator/prometheus", "/chaos", "/chaos/reset"})
    void neverDelaysActuatorOrChaosPaths(String path) throws Exception {
        state.setLatency(5_000);
        MockFilterChain chain = new MockFilterChain();
        long start = System.nanoTime();

        filter.doFilter(get(path), new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(1));
    }

    private static MockHttpServletRequest get(String path) {
        return new MockHttpServletRequest("GET", path);
    }
}
