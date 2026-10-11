package dev.opspilot.chaos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ChaosStateTest {

    private final CompletableFuture<Integer> exitCode = new CompletableFuture<>();
    private final ChaosState state = new ChaosState(exitCode::complete);

    @AfterEach
    void tearDown() {
        state.close();
    }

    @Test
    void startsWithNoFaults() {
        assertThat(state.status()).isEqualTo(new ChaosStatus(0, 0.0, 0, 0, false));
    }

    @Test
    void acceptsValidValues() {
        state.setLatency(250);
        state.setErrorRate(0.5);

        assertThat(state.latencyMs()).isEqualTo(250);
        assertThat(state.errorRate()).isEqualTo(0.5);
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, ChaosState.MAX_LATENCY_MS + 1})
    void rejectsInvalidLatency(long ms) {
        assertThatIllegalArgumentException().isThrownBy(() -> state.setLatency(ms));
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.1, 1.01, Double.NaN})
    void rejectsInvalidErrorRate(double rate) {
        assertThatIllegalArgumentException().isThrownBy(() -> state.setErrorRate(rate));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, ChaosState.MAX_LEAK_MB_PER_SECOND + 1})
    void rejectsInvalidMemoryLeakRate(int mb) {
        assertThatIllegalArgumentException().isThrownBy(() -> state.startMemoryLeak(mb));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, ChaosState.MAX_CPU_SECONDS + 1})
    void rejectsInvalidCpuDuration(int seconds) {
        assertThatIllegalArgumentException().isThrownBy(() -> state.burnCpu(seconds));
    }

    @Test
    void memoryLeakGrowsUntilResetFreesIt() {
        state.startMemoryLeak(2);

        awaitTrue(() -> state.leakedBytes() >= 2L * 1024 * 1024, Duration.ofSeconds(3));
        assertThat(state.memoryLeakMbPerSecond()).isEqualTo(2);

        state.reset();

        assertThat(state.leakedBytes()).isZero();
        assertThat(state.memoryLeakMbPerSecond()).isZero();
    }

    @Test
    void cpuBurnStopsOnReset() {
        state.burnCpu(30);
        assertThat(state.cpuBurnActive()).isTrue();

        state.reset();

        assertThat(state.cpuBurnActive()).isFalse();
    }

    @Test
    void crashCallsExitHookAfterDelay() throws Exception {
        long start = System.nanoTime();

        state.crash();

        assertThat(exitCode.get(2, TimeUnit.SECONDS)).isEqualTo(ChaosState.CRASH_EXIT_CODE);
        assertThat(Duration.ofNanos(System.nanoTime() - start))
                .isGreaterThanOrEqualTo(Duration.ofMillis(ChaosState.CRASH_DELAY_MS));
    }

    @Test
    void resetClearsRequestFaults() {
        state.setLatency(100);
        state.setErrorRate(1.0);

        state.reset();

        assertThat(state.status()).isEqualTo(new ChaosStatus(0, 0.0, 0, 0, false));
    }

    private static void awaitTrue(BooleanSupplier condition, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("condition not met within " + timeout);
            }
            Thread.onSpinWait();
        }
    }
}
