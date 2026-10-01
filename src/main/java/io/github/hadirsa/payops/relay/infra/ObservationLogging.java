package io.github.hadirsa.payops.relay.infra;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Prints every Micrometer observation the A2A server records, so its taskId and contextId are visible in the log. */
@Configuration
@ConditionalOnProperty(name = "relay.log-observations", havingValue = "true")
class ObservationLogging {

    private static final Logger log = LoggerFactory.getLogger("observation");

    @Bean
    @ConditionalOnMissingBean
    ObservationRegistry observationRegistry() {
        var registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(new ObservationHandler<Observation.Context>() {
            @Override
            public boolean supportsContext(Observation.Context context) {
                return context.getName() != null && context.getName().startsWith("a2a.");
            }

            @Override
            public void onStop(Observation.Context context) {
                log.info("OBSERVATION {} {} {}", context.getName(), context.getLowCardinalityKeyValues(),
                        context.getHighCardinalityKeyValues());
            }
        });
        return registry;
    }
}
