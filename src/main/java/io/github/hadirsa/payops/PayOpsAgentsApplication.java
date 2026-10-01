package io.github.hadirsa.payops;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PayOpsAgentsApplication {

    public static void main(String[] args) {
        SpringApplication.run(PayOpsAgentsApplication.class, args);
    }

    /** One clock for deadline maths, so tests can pin time. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
