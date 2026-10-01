package io.github.hadirsa.payops.dispute.infra.stripe;

import com.stripe.StripeClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "dispute.provider", havingValue = "stripe")
class StripeConfig {

    @Bean
    StripeClient stripeClient(StripeProperties properties) {
        var builder = StripeClient.builder()
                .setApiKey(properties.apiKey())
                .setConnectTimeout((int) properties.connectTimeout().toMillis())
                .setReadTimeout((int) properties.readTimeout().toMillis())
                .setMaxNetworkRetries(properties.maxNetworkRetries());
        if (properties.apiBase() != null && !properties.apiBase().isBlank()) {
            builder.setApiBase(properties.apiBase());
        }
        return builder.build();
    }
}
