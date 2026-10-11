package dev.opspilot.chaos;

import java.util.concurrent.ThreadLocalRandom;

import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Activates fault injection in any servlet application that has chaos-lib on its classpath.
 * Disable with {@code opspilot.chaos.enabled=false}.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "opspilot.chaos", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ChaosAutoConfiguration {

    /**
     * Runs just after Spring's HTTP observation filter (HIGHEST_PRECEDENCE + 1), so injected latency
     * shows up in {@code http.server.requests} exactly like real slowness.
     */
    static final int FILTER_ORDER = Ordered.HIGHEST_PRECEDENCE + 10;

    @Bean
    @ConditionalOnMissingBean
    public ChaosState chaosState() {
        return new ChaosState();
    }

    @Bean
    public FilterRegistrationBean<ChaosFilter> chaosFilter(ChaosState state) {
        FilterRegistrationBean<ChaosFilter> registration = new FilterRegistrationBean<>(new ChaosFilter(state));
        registration.setOrder(FILTER_ORDER);
        return registration;
    }

    @Bean
    public WebMvcConfigurer chaosErrorInterceptorConfigurer(ChaosState state) {
        ChaosErrorInterceptor interceptor =
                new ChaosErrorInterceptor(state, () -> ThreadLocalRandom.current().nextDouble());
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(interceptor)
                        .excludePathPatterns("/actuator", "/actuator/**", "/chaos", "/chaos/**");
            }
        };
    }

    @Bean
    public ChaosController chaosController(ChaosState state) {
        return new ChaosController(state);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(MeterBinder.class)
    static class ChaosMetricsConfiguration {

        @Bean
        public ChaosMetrics chaosMetrics(ChaosState state) {
            return new ChaosMetrics(state);
        }
    }
}
