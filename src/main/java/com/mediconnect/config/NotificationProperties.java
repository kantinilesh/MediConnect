package com.mediconnect.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Strongly-typed config for the notification dispatcher.
 *
 * <p>Bound from {@code mediconnect.notification.*} in application.yml.
 * All fields have safe defaults so the app starts even without explicit config.
 */
@Configuration
@ConfigurationProperties(prefix = "mediconnect.notification")
@Data
public class NotificationProperties {

    /** Capacity of the {@link java.util.concurrent.LinkedBlockingQueue}. */
    private int queueCapacity = 1000;

    /** Fixed thread pool size for notification worker threads. */
    private int threadPoolSize = 8;

    /**
     * Max time (ms) the event listener will wait to enqueue a task before
     * dropping it (backpressure). Keeps HTTP threads from blocking indefinitely.
     */
    private long offerTimeoutMs = 200;

    /** Seconds to wait during graceful shutdown before forcing {@code shutdownNow()}. */
    private int shutdownTimeoutSeconds = 30;

    private MockClientConfig mockClient = new MockClientConfig();
    private RetryConfig retry = new RetryConfig();

    // ── Nested config groups ──────────────────────────────────────────────────

    @Data
    public static class MockClientConfig {
        /**
         * Fraction [0.0, 1.0] of send attempts that will throw a simulated
         * provider failure. 0.2 = 20% failure rate.
         */
        private double failureRate = 0.2;

        /** Lower bound of simulated network latency. */
        private int minLatencyMs = 5;

        /** Upper bound of simulated network latency. */
        private int maxLatencyMs = 15;
    }

    @Data
    public static class RetryConfig {
        /** Total attempts including the initial one (maxAttempts=3 → 1 try + 2 retries). */
        private int maxAttempts = 3;

        /** Base delay (ms) for first retry. Doubles on each subsequent retry. */
        private long baseDelayMs = 200;

        /**
         * Jitter factor applied to base delay to spread retries.
         * 0.2 → ±20% randomisation. Prevents thundering-herd on provider recovery.
         */
        private double jitterFactor = 0.2;
    }
}
