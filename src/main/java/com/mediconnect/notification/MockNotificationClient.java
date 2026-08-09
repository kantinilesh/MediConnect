package com.mediconnect.notification;

import com.mediconnect.config.NotificationProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Simulates an external email/SMS notification provider.
 *
 * <p>Configured via {@link NotificationProperties.MockClientConfig}:
 * <ul>
 *   <li>{@code failureRate}   — fraction [0,1] of sends that throw a simulated failure</li>
 *   <li>{@code minLatencyMs}  — lower bound of artificial network latency</li>
 *   <li>{@code maxLatencyMs}  — upper bound of artificial network latency</li>
 * </ul>
 *
 * <p><b>Thread safety</b>: this class is <em>stateless</em> except for the
 * constructor-injected config (immutable after init). All randomness uses
 * {@link ThreadLocalRandom} — each thread gets its own PRNG with no contention.
 * The class is safe to call concurrently from all worker threads without locks.
 */
@Component
@Slf4j
public class MockNotificationClient {

    private final double failureRate;
    private final int minLatencyMs;
    private final int maxLatencyMs;

    public MockNotificationClient(NotificationProperties props) {
        this.failureRate   = props.getMockClient().getFailureRate();
        this.minLatencyMs  = props.getMockClient().getMinLatencyMs();
        this.maxLatencyMs  = props.getMockClient().getMaxLatencyMs();
    }

    /**
     * Simulate sending a notification to an external provider.
     *
     * <p>Blocks for [{@code minLatencyMs}, {@code maxLatencyMs}] ms to mimic
     * a real network call, then randomly throws {@link NotificationSendException}
     * at the configured failure rate.
     *
     * @param event the notification event to send
     * @throws NotificationSendException on simulated provider failure
     * @throws InterruptedException      if the worker thread is interrupted during sleep
     */
    public void send(AppointmentNotificationEvent event)
            throws NotificationSendException, InterruptedException {

        // Simulate network latency (uses thread-local RNG — no shared state)
        if (maxLatencyMs > 0) {
            int latency = minLatencyMs == maxLatencyMs
                    ? minLatencyMs
                    : minLatencyMs + ThreadLocalRandom.current().nextInt(maxLatencyMs - minLatencyMs + 1);
            Thread.sleep(latency);
        }

        // Simulate random provider failure
        if (ThreadLocalRandom.current().nextDouble() < failureRate) {
            throw new NotificationSendException(
                    String.format("Simulated provider failure for %s → %s",
                            event.getEventType(), event.getPatientEmail()));
        }

        log.debug("[MockClient] SENT {} → {} (appt={})",
                event.getEventType(), event.getPatientEmail(), event.getAppointmentId());
    }

    // ── Checked exception ─────────────────────────────────────────────────────

    public static class NotificationSendException extends Exception {
        public NotificationSendException(String message) {
            super(message);
        }
    }
}
