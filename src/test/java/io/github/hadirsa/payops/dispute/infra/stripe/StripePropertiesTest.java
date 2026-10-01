package io.github.hadirsa.payops.dispute.infra.stripe;

import java.time.Duration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StripePropertiesTest {

    private static StripeProperties props(String key, boolean allowLive) {
        return new StripeProperties(key, null, allowLive, null, Duration.ofSeconds(10), Duration.ofSeconds(30), 2);
    }

    @Test
    void acceptsTestAndRestrictedTestKeys() {
        assertDoesNotThrow(() -> props("sk_test_abc", false));
        assertDoesNotThrow(() -> props("rk_test_abc", false));
    }

    @Test
    void refusesLiveKeysUnlessExplicitlyAllowed() {
        var e = assertThrows(IllegalStateException.class, () -> props("sk_live_abc", false));
        assertTrue(e.getMessage().contains("live"));
        assertThrows(IllegalStateException.class, () -> props("rk_live_abc", false));
        assertDoesNotThrow(() -> props("sk_live_abc", true));
    }

    @Test
    void refusesMissingOrNonSecretKeysWithAHelpfulMessage() {
        assertTrue(assertThrows(IllegalStateException.class, () -> props(null, false))
                .getMessage().contains("STRIPE_API_KEY"));
        assertThrows(IllegalStateException.class, () -> props("  ", false));
        assertThrows(IllegalStateException.class, () -> props("pk_test_publishable", false));
    }

    @Test
    void webhookIsEnabledOnlyWithASecret() {
        assertFalse(props("sk_test_abc", false).webhookEnabled());
        assertTrue(new StripeProperties("sk_test_abc", "whsec_x", false, null, Duration.ZERO, Duration.ZERO, 0)
                .webhookEnabled());
    }
}
