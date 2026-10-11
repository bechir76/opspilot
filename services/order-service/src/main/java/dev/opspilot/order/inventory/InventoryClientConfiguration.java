package dev.opspilot.order.inventory;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(InventoryProperties.class)
public class InventoryClientConfiguration {

    /**
     * Built from Spring Boot's auto-configured {@link RestClient.Builder} (a prototype bean, so this is our
     * own copy) to inherit its observation setup: every call is recorded in {@code http.client.requests}.
     */
    @Bean
    public RestClient inventoryRestClient(RestClient.Builder builder, InventoryProperties properties) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(properties.connectTimeout())
                .withReadTimeout(properties.readTimeout());
        return builder
                .baseUrl(properties.baseUrl())
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .build();
    }
}
