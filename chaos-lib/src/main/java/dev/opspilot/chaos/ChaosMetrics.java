package dev.opspilot.chaos;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;

/**
 * Exposes the active faults as gauges, so dashboards (and later the AI agent) can tell
 * injected chaos apart from organic failures.
 */
public class ChaosMetrics implements MeterBinder {

    private final ChaosState state;

    public ChaosMetrics(ChaosState state) {
        this.state = state;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("opspilot.chaos.latency", state, ChaosState::latencyMs)
                .description("Latency injected into every business request")
                .baseUnit("milliseconds")
                .register(registry);
        Gauge.builder("opspilot.chaos.error.rate", state, ChaosState::errorRate)
                .description("Fraction of business requests failed with HTTP 500")
                .register(registry);
        Gauge.builder("opspilot.chaos.leaked", state, ChaosState::leakedBytes)
                .description("Memory deliberately leaked")
                .baseUnit("bytes")
                .register(registry);
    }
}
