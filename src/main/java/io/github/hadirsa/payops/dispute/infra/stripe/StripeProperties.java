package io.github.hadirsa.payops.dispute.infra.stripe;

import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Only bound when {@code dispute.provider=stripe}, so demo mode never needs a Stripe key.
 *
 * @param apiKey        a secret or restricted key; live keys are refused unless {@code allowLive} is set
 * @param webhookSecret signing secret of the webhook endpoint (starts with {@code whsec_}); blank disables the webhook
 * @param allowLive     set to true only when you deliberately want to run against real money
 * @param apiBase       override for tests or a proxy; blank uses Stripe's API
 */
@ConditionalOnProperty(name = "dispute.provider", havingValue = "stripe")
@ConfigurationProperties("stripe")
public record StripeProperties(
        String apiKey,
        String webhookSecret,
        @DefaultValue("false") boolean allowLive,
        String apiBase,
        @DefaultValue("10s") Duration connectTimeout,
        @DefaultValue("30s") Duration readTimeout,
        @DefaultValue("2") int maxNetworkRetries
) {

    public StripeProperties {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "dispute.provider=stripe needs stripe.api-key (set STRIPE_API_KEY to a sk_test_ key)");
        }
        if (!(apiKey.startsWith("sk_") || apiKey.startsWith("rk_"))) {
            throw new IllegalStateException("stripe.api-key must be a secret (sk_) or restricted (rk_) key");
        }
        if (!allowLive && (apiKey.startsWith("sk_live_") || apiKey.startsWith("rk_live_"))) {
            throw new IllegalStateException(
                    "Refusing to start with a live Stripe key. Use a test key, or set stripe.allow-live=true on purpose.");
        }
    }

    public boolean webhookEnabled() {
        return webhookSecret != null && !webhookSecret.isBlank();
    }
}
