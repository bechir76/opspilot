package dev.opspilot.chaos;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

class ChaosAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ChaosAutoConfiguration.class));

    @Test
    void enabledByDefaultInServletApps() {
        runner.run(context -> assertThat(context)
                .hasSingleBean(ChaosState.class)
                .hasSingleBean(ChaosController.class)
                .hasSingleBean(ChaosMetrics.class)
                .hasBean("chaosFilter")
                .hasBean("chaosErrorInterceptorConfigurer"));
    }

    @Test
    void disabledByProperty() {
        runner.withPropertyValues("opspilot.chaos.enabled=false")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(ChaosState.class)
                        .doesNotHaveBean("chaosFilter")
                        .doesNotHaveBean("chaosErrorInterceptorConfigurer"));
    }

    @Test
    void notActiveOutsideServletApps() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ChaosAutoConfiguration.class))
                .run(context -> assertThat(context).doesNotHaveBean(ChaosState.class));
    }

    @Test
    void registersGauges() {
        runner.run(context -> {
            SimpleMeterRegistry registry = new SimpleMeterRegistry();
            context.getBean(ChaosMetrics.class).bindTo(registry);
            context.getBean(ChaosState.class).setLatency(42);

            assertThat(registry.get("opspilot.chaos.latency").gauge().value()).isEqualTo(42.0);
            assertThat(registry.get("opspilot.chaos.error.rate").gauge().value()).isZero();
            assertThat(registry.get("opspilot.chaos.leaked").gauge().value()).isZero();
        });
    }
}
